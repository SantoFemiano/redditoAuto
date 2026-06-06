package com.santofem.redditoauto.service;

import com.santofem.redditoauto.config.BolloAciProperties;
import com.santofem.redditoauto.entity.Motorizzazione;
import com.santofem.redditoauto.entity.enums.TipoCarburante;
import com.santofem.redditoauto.repository.MotorizzazioneRepository;
import com.santofem.redditoauto.service.dto.CalcoloRequestDTO;
import com.santofem.redditoauto.service.dto.CalcoloRispostaDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Servizio di calcolo della sostenibilità economica di un'auto.
 *
 * Formula rata francese (ammortamento a rate costanti):
 *   R = P * [ i(1+i)^n ] / [ (1+i)^n - 1 ]
 *   dove P = capitale finanziato, i = TAN/12, n = numero rate
 *
 * Bollo ACI Italia:
 *   Tariffe caricate da BolloAciProperties (application.properties).
 *   Aggiornabile senza ricompilare il codice.
 *
 * Giudizio di sostenibilità (% reddito assorbita dall'auto):
 *   OTTIMO      → ≤ 20%
 *   ACCETTABILE → ≤ 30%
 *   ATTENZIONE  → ≤ 40%
 *   CRITICO     → > 40%
 *
 * @Cacheable su calcola() con chiave composta: i risultati identici
 * non vengono ricalcolati per 10 minuti (TTL Caffeine in CacheConfig).
 * La chiave usa stripTrailingZeros().toPlainString() sui BigDecimal per
 * garantire che valori numericamente identici (es: 2000 vs 2000.00)
 * producano la stessa chiave cache.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CalcoloSostenibilitaService {

    private final MotorizzazioneRepository motorizzazioneRepository;
    private final BolloAciProperties bolloAciProperties;

    // ── Pneumatici ────────────────────────────────────────────
    private static final BigDecimal PNEUMATICI_STANDARD = new BigDecimal("400.00");
    private static final BigDecimal PNEUMATICI_RUNFLAT  = new BigDecimal("700.00");
    private static final int        PNEUMATICI_MESI     = 36;

    // ── Fallback consumi/costi ──────────────────────────────
    private static final BigDecimal CONSUMO_FALLBACK      = new BigDecimal("8.00");
    private static final BigDecimal CONSUMO_EV_KWH_100KM  = new BigDecimal("16.00");
    private static final BigDecimal TAGLIANDO_BASE_FB     = new BigDecimal("250.00");
    private static final BigDecimal TAGLIANDO_MAIOR_FB    = new BigDecimal("600.00");
    private static final int        INTERVALLO_BASE_FB    = 15_000;
    private static final int        INTERVALLO_MAIOR_FB   = 60_000;

    // ── Soglie sostenibilità ──────────────────────────────
    private static final BigDecimal SOGLIA_OTTIMO      = new BigDecimal("20.00");
    private static final BigDecimal SOGLIA_ACCETTABILE = new BigDecimal("30.00");
    private static final BigDecimal SOGLIA_ATTENZIONE  = new BigDecimal("40.00");

    // =============================================================
    // ENTRY POINT PUBBLICO
    // =============================================================

    /**
     * Calcola la sostenibilità economica mensile completa di un'auto.
     * Il risultato è cacheable: richieste identiche (stessa motorizzazione,
     * reddito e km) vengono servite dalla cache Caffeine senza query al DB.
     *
     * La chiave è normalizzata: i BigDecimal vengono convertiti con
     * stripTrailingZeros().toPlainString() per evitare cache miss su
     * valori identici con rappresentazioni diverse (2000 vs 2000.0).
     */
    @Cacheable(
            value = "calcoli",
            key = "#request.motorizzazioneId + '_'
               + T(com.santofem.redditoauto.service.CalcoloSostenibilitaService).normalizeKey(#request.redditoNettoMensile) + '_'
               + #request.kmMensiliStimati + '_'
               + T(com.santofem.redditoauto.service.CalcoloSostenibilitaService).normalizeKey(#request.tanPercentuale)"
    )
    public CalcoloRispostaDTO calcola(CalcoloRequestDTO request) {

        Motorizzazione m = motorizzazioneRepository.findById(request.getMotorizzazioneId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Motorizzazione non trovata con id: " + request.getMotorizzazioneId()));

        BigDecimal tanDecimale = request.getTanPercentuale()
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);

        BigDecimal prezzoAuto   = m.getPrezzoListinoEur() != null
                ? m.getPrezzoListinoEur() : BigDecimal.ZERO;
        BigDecimal anticipo     = request.getAcconto();
        BigDecimal prezzoFinanz = prezzoAuto.subtract(anticipo).max(BigDecimal.ZERO);
        int        durata       = request.getDurataFinanziamentoMesi();
        int        kmMensili    = request.getKmMensiliStimati();
        BigDecimal prezzoCarb   = request.getPrezzoCombustibileLitro();

        BigDecimal rata              = calcolaRataFrancese(prezzoFinanz, tanDecimale, durata);
        BigDecimal interessiTotali   = rata.multiply(BigDecimal.valueOf(durata))
                .subtract(prezzoFinanz).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal costoTotaleFinanz = rata.multiply(BigDecimal.valueOf(durata))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal costoCarb         = calcolaCostoCarburanteMensile(m, kmMensili, prezzoCarb);
        BigDecimal costoPneum        = calcolaCostoPneumaticiMensile(m);
        BigDecimal[] tagliandi       = calcolaCostoTagliandiMensile(m, kmMensili);
        BigDecimal bolloMensile      = calcolaBolloMensile(m);
        BigDecimal assicMensile      = calcolaAssicurazioneMensile(request, m);

        BigDecimal totCostiVivi = costoCarb
                .add(costoPneum)
                .add(tagliandi[0])
                .add(tagliandi[1])
                .add(bolloMensile)
                .add(assicMensile);

        BigDecimal totMensile = rata.add(totCostiVivi);

        BigDecimal reddito = request.getRedditoNettoMensile();
        BigDecimal percReddito = totMensile
                .divide(reddito, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        String  giudizio    = calcolaGiudizio(percReddito);
        String  consigli    = generaConsigli(percReddito, m, durata, anticipo);
        boolean sostenibile = percReddito.compareTo(SOGLIA_ACCETTABILE) <= 0;

        String label = String.format("%s %s %s (%d)",
                m.getModello().getMarca().getNome(),
                m.getModello().getNome(),
                m.getNomeMotore(),
                m.getAnnoProduzione());

        log.debug("Calcolo completato: {} \u2192 totale={}€/mese ({}%)", label, totMensile, percReddito);

        return CalcoloRispostaDTO.builder()
                .marcaModelloMotore(label)
                .prezzoFinanziato(prezzoFinanz)
                .rataFinanziamentoMensile(rata)
                .durataFinanziamentoMesi(durata)
                .tanPercentuale(request.getTanPercentuale())
                .costoTotaleFinanziamento(costoTotaleFinanz)
                .interessiTotali(interessiTotali)
                .costoCarburanteMensile(costoCarb)
                .costoPneumaticiMensile(costoPneum)
                .costoTagliandoMensile(tagliandi[0])
                .costoTagliandoMaiorMensile(tagliandi[1])
                .costoBolloMensile(bolloMensile)
                .costoAssicurazioneMensile(assicMensile)
                .totaleCostiViviMensili(totCostiVivi)
                .totaleMensileAutoCompleto(totMensile)
                .redditoNettoMensile(reddito)
                .percentualeRedditoImpegnata(percReddito)
                .sostenibile(sostenibile)
                .giudizio(giudizio)
                .messaggioConsigli(consigli)
                .build();
    }

    /**
     * Normalizza un BigDecimal per l'uso come chiave cache.
     * Rimuove gli zeri finali e usa la rappresentazione plain (senza notazione scientifica).
     * Metodo statico per essere richiamabile nelle SpEL expression di @Cacheable.
     *
     * Esempi: 2000.00 -> "2000", 7.50 -> "7.5", 1.85 -> "1.85"
     */
    public static String normalizeKey(BigDecimal value) {
        if (value == null) return "null";
        return value.stripTrailingZeros().toPlainString();
    }

    // =============================================================
    // RATA FRANCESE: R = P * i(1+i)^n / ((1+i)^n - 1)
    // =============================================================
    private BigDecimal calcolaRataFrancese(BigDecimal capitale, BigDecimal tanDecimale, int n) {
        if (capitale.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        BigDecimal i = tanDecimale.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        if (i.compareTo(BigDecimal.ZERO) == 0) {
            return capitale.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
        }

        MathContext mc     = new MathContext(15, RoundingMode.HALF_UP);
        BigDecimal unoPiuI = BigDecimal.ONE.add(i);
        BigDecimal potenza = unoPiuI.pow(n, mc);

        return capitale
                .multiply(i).multiply(potenza)
                .divide(potenza.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
    }

    // =============================================================
    // COSTO CARBURANTE MENSILE
    // =============================================================
    private BigDecimal calcolaCostoCarburanteMensile(
            Motorizzazione m, int kmMensili, BigDecimal prezzoCarburante) {

        TipoCarburante tipo = m.getTipoCarburante();

        if (tipo == TipoCarburante.ELETTRICO) {
            return CONSUMO_EV_KWH_100KM
                    .multiply(BigDecimal.valueOf(kmMensili))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    .multiply(prezzoCarburante)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal consumo = m.getConsumoMedioLitri100km() != null
                ? m.getConsumoMedioLitri100km() : CONSUMO_FALLBACK;

        if (tipo == TipoCarburante.IBRIDO_PLUGIN) {
            BigDecimal kmTermici   = BigDecimal.valueOf(kmMensili).multiply(new BigDecimal("0.40"));
            BigDecimal kmElettrico = BigDecimal.valueOf(kmMensili).multiply(new BigDecimal("0.60"));
            BigDecimal costoTerm   = consumo.multiply(kmTermici)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    .multiply(prezzoCarburante);
            BigDecimal costoElett  = CONSUMO_EV_KWH_100KM.multiply(kmElettrico)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("0.25"));
            return costoTerm.add(costoElett).setScale(2, RoundingMode.HALF_UP);
        }

        return consumo
                .multiply(BigDecimal.valueOf(kmMensili))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .multiply(prezzoCarburante)
                .setScale(2, RoundingMode.HALF_UP);
    }

    // =============================================================
    // COSTO PNEUMATICI MENSILE
    // =============================================================
    private BigDecimal calcolaCostoPneumaticiMensile(Motorizzazione m) {
        BigDecimal costoSet = Boolean.TRUE.equals(m.getRunFlat())
                ? PNEUMATICI_RUNFLAT : PNEUMATICI_STANDARD;
        return costoSet.divide(BigDecimal.valueOf(PNEUMATICI_MESI), 2, RoundingMode.HALF_UP);
    }

    // =============================================================
    // COSTO TAGLIANDI MENSILE — [0]=ordinario, [1]=maior
    // =============================================================
    private BigDecimal[] calcolaCostoTagliandiMensile(Motorizzazione m, int kmMensili) {
        BigDecimal costoBase  = nvl(m.getCostoTagliandoBaseEur(),  TAGLIANDO_BASE_FB);
        BigDecimal costoMaior = nvl(m.getCostoTagliandoMaiorEur(), TAGLIANDO_MAIOR_FB);
        int        kmBase     = nvl(m.getIntervalloTagliandoKm(),      INTERVALLO_BASE_FB);
        int        kmMaior    = nvl(m.getIntervalloTagliandoMaiorKm(), INTERVALLO_MAIOR_FB);

        int    kmAnnui             = kmMensili * 12;
        double tagliandiMaiorAnno  = (double) kmAnnui / kmMaior;
        double tagliandiBaseAnno   = (double) kmAnnui / kmBase;
        double tagliandiOrdAnno    = Math.max(0, tagliandiBaseAnno - tagliandiMaiorAnno);

        BigDecimal mensileOrd   = costoBase.multiply(BigDecimal.valueOf(tagliandiOrdAnno))
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        BigDecimal mensileMaior = costoMaior.multiply(BigDecimal.valueOf(tagliandiMaiorAnno))
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

        return new BigDecimal[]{mensileOrd, mensileMaior};
    }

    // =============================================================
    // BOLLO ACI MENSILE — tariffe da BolloAciProperties
    // =============================================================
    private BigDecimal calcolaBolloMensile(Motorizzazione m) {
        if (m.getTipoCarburante() == TipoCarburante.ELETTRICO) return BigDecimal.ZERO;

        int        kw     = m.getPotenzaKw() != null ? m.getPotenzaKw() : 70;
        BigDecimal bBase  = bolloAciProperties.baseKw();
        BigDecimal bExtra = bolloAciProperties.extraKw();

        boolean isDiesel = m.getTipoCarburante() == TipoCarburante.DIESEL
                        || m.getTipoCarburante() == TipoCarburante.IBRIDO_DIESEL;
        if (isDiesel) {
            bBase  = bBase.add(bolloAciProperties.dieselBase());
            bExtra = bExtra.add(bolloAciProperties.dieselExtra());
        }

        BigDecimal bolloAnnuo = kw <= bolloAciProperties.sogliaKw()
                ? bBase.multiply(BigDecimal.valueOf(kw))
                : bBase.multiply(BigDecimal.valueOf(bolloAciProperties.sogliaKw()))
                        .add(bExtra.multiply(BigDecimal.valueOf(kw - bolloAciProperties.sogliaKw())));

        return bolloAnnuo.max(bolloAciProperties.minimo())
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    // =============================================================
    // ASSICURAZIONE MENSILE
    // =============================================================
    private BigDecimal calcolaAssicurazioneMensile(CalcoloRequestDTO request, Motorizzazione m) {
        if (request.getAssicurazioneAnnuaEur() != null
                && request.getAssicurazioneAnnuaEur().compareTo(BigDecimal.ZERO) > 0) {
            return request.getAssicurazioneAnnuaEur()
                    .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        }
        int gruppo = m.getGruppoAssicurativo() != null
                ? Math.max(1, Math.min(20, m.getGruppoAssicurativo())) : 10;
        double stimaAnnua = 400.0 + (gruppo - 1) * (1400.0 / 19.0);
        return BigDecimal.valueOf(stimaAnnua)
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    // =============================================================
    // GIUDIZIO E CONSIGLI
    // =============================================================
    private String calcolaGiudizio(BigDecimal perc) {
        if (perc.compareTo(SOGLIA_OTTIMO) <= 0)      return "OTTIMO";
        if (perc.compareTo(SOGLIA_ACCETTABILE) <= 0) return "ACCETTABILE";
        if (perc.compareTo(SOGLIA_ATTENZIONE) <= 0)  return "ATTENZIONE";
        return "CRITICO";
    }

    private String generaConsigli(BigDecimal perc, Motorizzazione m, int durata, BigDecimal anticipo) {
        StringBuilder sb   = new StringBuilder();
        String        giud = calcolaGiudizio(perc);

        switch (giud) {
            case "OTTIMO"      -> sb.append("Ottima scelta! L'auto assorbe meno del 20% del tuo reddito. ");
            case "ACCETTABILE" -> sb.append("La spesa è nella norma (20-30% del reddito). Gestibile con buona pianificazione. ");
            case "ATTENZIONE"  -> sb.append("Attenzione: l'auto assorbe il ").append(perc).append("% del reddito. ");
            case "CRITICO"     -> sb.append("CRITICO: l'auto assorbe il ").append(perc).append("% del reddito. Valuta alternative. ");
        }

        if (durata > 72)
            sb.append("Considera una durata del finanziamento più breve per ridurre gli interessi totali. ");
        if (anticipo.compareTo(BigDecimal.ZERO) == 0)
            sb.append("Un acconto ridurrebbe la rata mensile significativamente. ");
        if (Boolean.TRUE.equals(m.getRunFlat()))
            sb.append("I pneumatici run-flat di questo modello hanno un costo di sostituzione elevato. ");
        if (m.getTipoCarburante() == TipoCarburante.DIESEL)
            sb.append("Verifica la compatibilità con le ZTL della tua città. ");

        return sb.toString().trim();
    }

    // =============================================================
    // UTILITY
    // =============================================================
    private static BigDecimal nvl(BigDecimal val, BigDecimal fallback) {
        return val != null ? val : fallback;
    }

    private static int nvl(Integer val, int fallback) {
        return val != null ? val : fallback;
    }
}

package com.santofem.redditoauto.service;

import com.santofem.redditoauto.entity.Motorizzazione;
import com.santofem.redditoauto.entity.enums.TipoCarburante;
import com.santofem.redditoauto.repository.MotorizzazioneRepository;
import com.santofem.redditoauto.service.dto.CalcoloRequestDTO;
import com.santofem.redditoauto.service.dto.CalcoloRispostaDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Service principale per il calcolo della sostenibilità economica di un'auto.
 *
 * Calcoli effettuati:
 *   1. Rata mensile con ammortamento francese (rata costante)
 *   2. Costo carburante mensile
 *   3. Costo pneumatici mensilizzato
 *   4. Costo tagliandi mensilizzato (ordinario + maggiore)
 *   5. Bollo ACI annuo → mensile (tariffa italiana kW-based)
 *   6. Assicurazione RC mensile (fornita o stimata per gruppo)
 *   7. Giudizio di sostenibilità (% reddito impegnata)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CalcoloSostenibilitaService {

    // -----------------------------------------------
    // COSTANTI
    // -----------------------------------------------

    /** Tariffa bollo ACI 2024: €2.58/kW per i primi 100 kW. */
    private static final BigDecimal BOLLO_TARIFFA_SOTTO_100KW = new BigDecimal("2.58");
    /** Tariffa bollo ACI 2024: €3.87/kW oltre i 100 kW. */
    private static final BigDecimal BOLLO_TARIFFA_OLTRE_100KW = new BigDecimal("3.87");

    /** Costo medio set 4 pneumatici standard (€/set). */
    private static final BigDecimal COSTO_PNEUMATICI_STANDARD = new BigDecimal("400.0");
    /** Costo medio set 4 pneumatici run-flat (€/set). */
    private static final BigDecimal COSTO_PNEUMATICI_RUN_FLAT  = new BigDecimal("620.0");
    /** Sostituzione pneumatici ogni N km (standard: 40000 km). */
    private static final int KM_VITA_PNEUMATICI = 40_000;

    /**
     * Stima assicurazione per gruppo (€/anno).
     * Gruppo 1-5: vetture utilitarie poco potenti.
     * Gruppo 15-20: supercar e auto di lusso ad alto rischio.
     */
    private static final BigDecimal[] ASSICURAZIONE_PER_GRUPPO = {
        BigDecimal.ZERO,           // [0] placeholder (gruppi partono da 1)
        new BigDecimal("600"),     // [1]  gruppo 1
        new BigDecimal("650"),     // [2]  gruppo 2
        new BigDecimal("700"),     // [3]
        new BigDecimal("750"),     // [4]
        new BigDecimal("820"),     // [5]
        new BigDecimal("900"),     // [6]
        new BigDecimal("980"),     // [7]
        new BigDecimal("1060"),    // [8]
        new BigDecimal("1150"),    // [9]
        new BigDecimal("1250"),    // [10]
        new BigDecimal("1380"),    // [11]
        new BigDecimal("1520"),    // [12]
        new BigDecimal("1700"),    // [13]
        new BigDecimal("1900"),    // [14]
        new BigDecimal("2100"),    // [15]
        new BigDecimal("2350"),    // [16]
        new BigDecimal("2650"),    // [17]
        new BigDecimal("3000"),    // [18]
        new BigDecimal("3500"),    // [19]
        new BigDecimal("4200"),    // [20]
    };

    /** Soglie % reddito per giudizio sostenibilità. */
    private static final BigDecimal SOGLIA_OTTIMO      = new BigDecimal("20");
    private static final BigDecimal SOGLIA_ACCETTABILE = new BigDecimal("30");
    private static final BigDecimal SOGLIA_ATTENZIONE  = new BigDecimal("40");

    private static final BigDecimal CENTO    = new BigDecimal("100");
    private static final BigDecimal DODICI   = new BigDecimal("12");
    private static final MathContext MC       = new MathContext(10, RoundingMode.HALF_UP);

    // -----------------------------------------------
    // DEPENDENCY
    // -----------------------------------------------

    private final MotorizzazioneRepository motorizzazioneRepository;

    // -----------------------------------------------
    // PUBLIC API
    // -----------------------------------------------

    /**
     * Calcola la sostenibilità economica completa per una motorizzazione.
     *
     * @param request DTO con reddito, parametri finanziamento e km mensili
     * @return DTO di risposta con rata, costi vivi e giudizio
     * @throws jakarta.persistence.EntityNotFoundException se la motorizzazione non esiste
     */
    public CalcoloRispostaDTO calcola(CalcoloRequestDTO request) {

        Motorizzazione auto = motorizzazioneRepository.findById(request.getMotorizzazioneId())
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                "Motorizzazione non trovata con id: " + request.getMotorizzazioneId()));

        log.info("Calcolo sostenibilità per: {} {} {} ({})",
            auto.getModello().getMarca().getNome(),
            auto.getModello().getNome(),
            auto.getNomeMotore(),
            auto.getAnnoProduzione());

        // 1. Rata finanziamento
        BigDecimal prezzoFinanziato = calcolaPrezzoFinanziato(auto, request);
        BigDecimal rata            = calcolaRataFrancese(prezzoFinanziato, request);
        BigDecimal costoTotale     = rata.multiply(BigDecimal.valueOf(request.getDurataFinanziamentoMesi()));
        BigDecimal interessi       = costoTotale.subtract(prezzoFinanziato);

        // 2. Costi vivi mensili
        BigDecimal costoCarburante       = calcolaCostoCarburanteMensile(auto, request);
        BigDecimal costoPneumatici       = calcolaCostoPneumaticiMensile(auto, request);
        BigDecimal costoTagliandoBase    = calcolaCostoTagliandoMensile(auto, request, false);
        BigDecimal costoTagliandoMaior   = calcolaCostoTagliandoMensile(auto, request, true);
        BigDecimal costoBollo            = calcolaBolloMensile(auto);
        BigDecimal costoAssicurazione    = calcolaAssicurazioneMensile(auto, request);

        // 3. Totali
        BigDecimal totaleCostiVivi = costoCarburante
            .add(costoPneumatici)
            .add(costoTagliandoBase)
            .add(costoTagliandoMaior)
            .add(costoBollo)
            .add(costoAssicurazione)
            .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totaleMensile = rata.add(totaleCostiVivi).setScale(2, RoundingMode.HALF_UP);

        // 4. Sostenibilità
        BigDecimal percReddito = totaleMensile
            .divide(request.getRedditoNettoMensile(), MC)
            .multiply(CENTO)
            .setScale(2, RoundingMode.HALF_UP);

        String giudizio         = calcolaGiudizio(percReddito);
        boolean sostenibile     = percReddito.compareTo(SOGLIA_ACCETTABILE) <= 0;
        String messaggioConsigli = generaConsigli(percReddito, auto, request);

        // 5. Build risposta
        String nomeCompleto = String.format("%s %s %s (%d)",
            auto.getModello().getMarca().getNome(),
            auto.getModello().getNome(),
            auto.getNomeMotore(),
            auto.getAnnoProduzione());

        return CalcoloRispostaDTO.builder()
            .marcaModelloMotore(nomeCompleto)
            .prezzoFinanziato(prezzoFinanziato)
            .rataFiananziamentoMensile(rata)
            .durataFinanziamentoMesi(request.getDurataFinanziamentoMesi())
            .tanPercentuale(request.getTanPercentuale())
            .costoTotaleFinanziamento(costoTotale.setScale(2, RoundingMode.HALF_UP))
            .interessiTotali(interessi.setScale(2, RoundingMode.HALF_UP))
            .costoCarburanteMensile(costoCarburante)
            .costoPneumaticiMensile(costoPneumatici)
            .costoTagliandoMensile(costoTagliandoBase)
            .costoTagliandoMaiorMensile(costoTagliandoMaior)
            .costoBolloMensile(costoBollo)
            .costoAssicurazioneMensile(costoAssicurazione)
            .totaleCostiViviMensili(totaleCostiVivi)
            .totaleMensileAutoCompleto(totaleMensile)
            .redditoNettoMensile(request.getRedditoNettoMensile())
            .percentualeRedditoImpegnata(percReddito)
            .sostenibile(sostenibile)
            .giudizio(giudizio)
            .messaggioConsigli(messaggioConsigli)
            .build();
    }

    // -----------------------------------------------
    // CALCOLI PRIVATI
    // -----------------------------------------------

    /**
     * Prezzo da finanziare = prezzo listino - acconto.
     * Se il prezzo listino non è disponibile nel DB, lancia eccezione.
     */
    private BigDecimal calcolaPrezzoFinanziato(Motorizzazione auto, CalcoloRequestDTO req) {
        if (auto.getPrezzoListinoEur() == null) {
            throw new IllegalStateException(
                "Prezzo listino non disponibile per: " + auto.getNomeMotore() +
                ". Aggiorna i dati tramite AI Extractor.");
        }
        BigDecimal prezzoFinanziato = auto.getPrezzoListinoEur().subtract(req.getAcconto());
        if (prezzoFinanziato.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                "L'acconto supera o uguaglia il prezzo del veicolo.");
        }
        return prezzoFinanziato.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Formula rata mensile con ammortamento FRANCESE (rata costante):
     *
     *         P * r
     * R = ───────────────
     *      1 - (1+r)^(-n)
     *
     * dove:
     *   P = capitale finanziato
     *   r = tasso mensile (TAN% / 12 / 100)
     *   n = numero rate (mesi)
     *
     * Caso degenere: TAN = 0% → R = P / n (divisione semplice, no divisione per zero).
     */
    private BigDecimal calcolaRataFrancese(BigDecimal prezzoFinanziato, CalcoloRequestDTO req) {
        int n = req.getDurataFinanziamentoMesi();
        BigDecimal tanAnnuo = req.getTanPercentuale();

        if (tanAnnuo.compareTo(BigDecimal.ZERO) == 0) {
            // TAN 0%: rata = P / n
            return prezzoFinanziato
                .divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
        }

        // r = TAN% / 12 / 100
        double r = tanAnnuo.doubleValue() / 12.0 / 100.0;
        double P = prezzoFinanziato.doubleValue();

        // R = P * r / (1 - (1+r)^(-n))
        double rata = (P * r) / (1.0 - Math.pow(1.0 + r, -n));

        return BigDecimal.valueOf(rata).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Costo carburante mensile.
     * Formula: km_mensili × (consumo_L/100km / 100) × prezzo_litro
     * Per i veicoli elettrici, consumoMedioLitri100km rappresenta kWh/100km.
     */
    private BigDecimal calcolaCostoCarburanteMensile(Motorizzazione auto, CalcoloRequestDTO req) {
        if (auto.getConsumoMedioLitri100km() == null) {
            log.warn("Consumo non disponibile per {}, uso stima 8 l/100km", auto.getNomeMotore());
            // Stima di fallback per non bloccare il calcolo
            return req.getPrezzoCombustibileLitro()
                .multiply(BigDecimal.valueOf(req.getKmMensiliStimati()))
                .multiply(new BigDecimal("0.08"))
                .setScale(2, RoundingMode.HALF_UP);
        }

        // consumo_litri_per_km = consumoMedio / 100
        BigDecimal consumoPerKm = auto.getConsumoMedioLitri100km()
            .divide(CENTO, 6, RoundingMode.HALF_UP);

        return consumoPerKm
            .multiply(BigDecimal.valueOf(req.getKmMensiliStimati()))
            .multiply(req.getPrezzoCombustibileLitro())
            .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Costo pneumatici mensilizzato.
     * Logica: (costo set / km_vita_pneumatici) × km_mensili
     * I run-flat costano ~55% in più e durano leggermente meno.
     */
    private BigDecimal calcolaCostoPneumaticiMensile(Motorizzazione auto, CalcoloRequestDTO req) {
        BigDecimal costoSet = Boolean.TRUE.equals(auto.getRunFlat())
            ? COSTO_PNEUMATICI_RUN_FLAT
            : COSTO_PNEUMATICI_STANDARD;

        // costo_per_km = costoSet / km_vita
        BigDecimal costoPerKm = costoSet
            .divide(BigDecimal.valueOf(KM_VITA_PNEUMATICI), 8, RoundingMode.HALF_UP);

        return costoPerKm
            .multiply(BigDecimal.valueOf(req.getKmMensiliStimati()))
            .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Costo tagliando mensilizzato.
     * Logica: (costo_tagliando / intervallo_km) × km_mensili
     *
     * @param maiorazione true = tagliando maggiore, false = ordinario
     */
    private BigDecimal calcolaCostoTagliandoMensile(
            Motorizzazione auto, CalcoloRequestDTO req, boolean maiorazione) {

        BigDecimal costo    = maiorazione ? auto.getCostoTagliandoMaiorEur()  : auto.getCostoTagliandoBaseEur();
        Integer intervallo  = maiorazione ? auto.getIntervalloTagliandoMaiorKm() : auto.getIntervalloTagliandoKm();

        if (costo == null || intervallo == null || intervallo == 0) {
            // Fallback se l'AI non ha estratto i dati di manutenzione
            if (!maiorazione) {
                log.warn("Costo tagliando non disponibile per {}, uso stima €15/mese", auto.getNomeMotore());
                return new BigDecimal("15.00");
            }
            return BigDecimal.ZERO;
        }

        BigDecimal costoPerKm = costo
            .divide(BigDecimal.valueOf(intervallo), 8, RoundingMode.HALF_UP);

        return costoPerKm
            .multiply(BigDecimal.valueOf(req.getKmMensiliStimati()))
            .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Bollo ACI annuo → mensile.
     * Tariffe Italia 2024:
     *   - Primi 100 kW: €2.58/kW
     *   - kW eccedenti i 100: €3.87/kW
     * I veicoli elettrici sono ESENTI (bollo = 0 per i primi 5 anni, poi ridotto).
     * Qui applichiamo esenzione totale per semplicità (conservativo per l'utente).
     */
    private BigDecimal calcolaBolloMensile(Motorizzazione auto) {
        if (auto.getPotenzaKw() == null) {
            log.warn("Potenza kW non disponibile per {}, bollo non calcolato", auto.getNomeMotore());
            return BigDecimal.ZERO;
        }

        // Elettrici: esenti
        if (TipoCarburante.ELETTRICO.equals(auto.getTipoCarburante())) {
            return BigDecimal.ZERO;
        }

        int kw = auto.getPotenzaKw();
        BigDecimal bolloAnnuo;

        if (kw <= 100) {
            bolloAnnuo = BOLLO_TARIFFA_SOTTO_100KW.multiply(BigDecimal.valueOf(kw));
        } else {
            // Primi 100 kW a €2.58, eccedenza a €3.87
            BigDecimal bolloBase      = BOLLO_TARIFFA_SOTTO_100KW.multiply(BigDecimal.valueOf(100));
            BigDecimal bolloEccedenza = BOLLO_TARIFFA_OLTRE_100KW.multiply(BigDecimal.valueOf(kw - 100));
            bolloAnnuo = bolloBase.add(bolloEccedenza);
        }

        return bolloAnnuo.divide(DODICI, 2, RoundingMode.HALF_UP);
    }

    /**
     * Costo assicurazione mensile.
     * Se l'utente ha fornito il valore reale, usa quello.
     * Altrimenti stima dal gruppo assicurativo del veicolo.
     */
    private BigDecimal calcolaAssicurazioneMensile(Motorizzazione auto, CalcoloRequestDTO req) {
        // Usa il valore reale se fornito dall'utente
        if (req.getAssicurazioneAnnuaEur() != null
                && req.getAssicurazioneAnnuaEur().compareTo(BigDecimal.ZERO) > 0) {
            return req.getAssicurazioneAnnuaEur()
                .divide(DODICI, 2, RoundingMode.HALF_UP);
        }

        // Stima da gruppo assicurativo
        Integer gruppo = auto.getGruppoAssicurativo();
        if (gruppo == null || gruppo < 1 || gruppo > 20) {
            log.warn("Gruppo assicurativo non disponibile/valido per {}, uso €80/mese", auto.getNomeMotore());
            return new BigDecimal("80.00");
        }

        return ASSICURAZIONE_PER_GRUPPO[gruppo]
            .divide(DODICI, 2, RoundingMode.HALF_UP);
    }

    // -----------------------------------------------
    // GIUDIZIO DI SOSTENIBILITÀ
    // -----------------------------------------------

    /**
     * Giudizio basato sulla percentuale del reddito impegnata.
     * Soglie ispirate alle linee guida finanziarie comuni:
     *   < 20% → OTTIMO
     *   20-30% → ACCETTABILE
     *   30-40% → ATTENZIONE
     *   > 40%  → CRITICO
     */
    private String calcolaGiudizio(BigDecimal percentuale) {
        if (percentuale.compareTo(SOGLIA_OTTIMO) < 0)       return "OTTIMO";
        if (percentuale.compareTo(SOGLIA_ACCETTABILE) < 0)  return "ACCETTABILE";
        if (percentuale.compareTo(SOGLIA_ATTENZIONE) < 0)   return "ATTENZIONE";
        return "CRITICO";
    }

    private String generaConsigli(BigDecimal perc, Motorizzazione auto, CalcoloRequestDTO req) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
            "Questa auto impegna il %.1f%% del tuo reddito netto mensile. ",
            perc.doubleValue()));

        if (perc.compareTo(SOGLIA_OTTIMO) < 0) {
            sb.append("Ottima scelta: il costo è ben al di sotto del 20% del reddito. "
                + "Hai ampio margine per gestire spese impreviste.");
        } else if (perc.compareTo(SOGLIA_ACCETTABILE) < 0) {
            sb.append("Scelta accettabile. Stai rispettando la regola del 30%. "
                + "Considera di mettere da parte un piccolo fondo per imprevisti.");
        } else if (perc.compareTo(SOGLIA_ATTENZIONE) < 0) {
            sb.append("Attenzione: superi il 30% del reddito. "
                + "Valuta un acconto più alto, una durata più lunga o un modello meno costoso.");
        } else {
            sb.append("Situazione critica: oltre il 40% del reddito assorbito dall'auto. "
                + "Si consiglia di riconsiderare il budget o orientarsi verso un veicolo usato/più economico.");
        }

        // Suggerimento specifico per carburante
        if (TipoCarburante.ELETTRICO.equals(auto.getTipoCarburante())) {
            sb.append(" Bonus: veicolo elettrico, zero bollo e costi energetici ridotti.");
        } else if (TipoCarburante.DIESEL.equals(auto.getTipoCarburante()
            ) && req.getKmMensiliStimati() < 1000) {
            sb.append(" Nota: con meno di 1000 km/mese un diesel raramente è conveniente rispetto a benzina/ibrido.");
        }

        return sb.toString();
    }
}

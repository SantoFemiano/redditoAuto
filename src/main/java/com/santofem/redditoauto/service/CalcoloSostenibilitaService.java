package com.santofem.redditoauto.service;

import com.santofem.redditoauto.dto.RisultatoCalcoloDTO;
import com.santofem.redditoauto.entity.Motorizzazione;
import com.santofem.redditoauto.entity.enums.TipoCarburante;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Servizio di calcolo della sostenibilità economica di un'auto.
 *
 * Formula rata francese (ammortamento a rate costanti):
 *   R = P * [ i(1+i)^n ] / [ (1+i)^n - 1 ]
 *   dove:
 *     P = capitale finanziato
 *     i = tasso mensile (TAN annuo / 12)
 *     n = numero rate mensili
 *
 * Bollo ACI (Italia 2024):
 *   - Benzina/GPL/Metano/Ibrido: 2.58 €/kW per i primi 100 kW,
 *                                 3.87 €/kW per i kW eccedenti i 100
 *   - Diesel: 2.58 €/kW fino a 100kW + 3.87 €/kW oltre, con maggiorazione 0.26/0.39 €/kW
 *   - Elettrici puri: esenti per i primi 5 anni, poi 50% riduzione
 *   - Minimo bollo: 27.00 €
 *
 * Costi mensili inclusi:
 *   1. Rata finanziamento
 *   2. Carburante (consumo × km/mese × prezzo carburante)
 *   3. Pneumatici (costo set / durata mesi stimata)
 *   4. Tagliandi (costo annuo medio / 12)
 *   5. Bollo (annuale / 12)
 *   6. Assicurazione RC (stimata per gruppo assicurativo)
 */
@Service
public class CalcoloSostenibilitaService {

    // --- Costanti bollo ACI ---
    private static final BigDecimal BOLLO_BASE_PER_KW        = new BigDecimal("2.58");
    private static final BigDecimal BOLLO_EXTRA_PER_KW       = new BigDecimal("3.87");
    private static final BigDecimal BOLLO_DIESEL_BASE        = new BigDecimal("0.26");
    private static final BigDecimal BOLLO_DIESEL_EXTRA       = new BigDecimal("0.39");
    private static final int        BOLLO_SOGLIA_KW          = 100;
    private static final BigDecimal BOLLO_MINIMO             = new BigDecimal("27.00");

    // --- Costi pneumatici stimati (€ per set di 4) ---
    private static final BigDecimal COSTO_PNEUMATICI_STANDARD = new BigDecimal("400.00");
    private static final BigDecimal COSTO_PNEUMATICI_RUNFLAT  = new BigDecimal("700.00");
    private static final int        DURATA_PNEUMATICI_MESI    = 36; // ~3 anni / 45.000 km

    // --- Prezzi carburante di riferimento (€/litro o €/kWh) ---
    private static final BigDecimal PREZZO_BENZINA   = new BigDecimal("1.75");
    private static final BigDecimal PREZZO_DIESEL    = new BigDecimal("1.65");
    private static final BigDecimal PREZZO_GPL       = new BigDecimal("0.75");
    private static final BigDecimal PREZZO_METANO    = new BigDecimal("1.20");  // €/kg
    private static final BigDecimal PREZZO_ELETTRICO = new BigDecimal("0.25");  // €/kWh

    // --- Parametri default ---
    private static final int        KM_MENSILI_DEFAULT    = 1500;
    private static final BigDecimal CONSUMO_EV_KWH_100KM  = new BigDecimal("16.00"); // kWh/100km medio EV

    /**
     * Punto di ingresso principale del calcolo.
     *
     * @param motorizzazione  entità con tutti i dati tecnici dell'auto
     * @param prezzoAuto      prezzo totale dell'auto (può differire dal listino per sconti)
     * @param anticipo        acconto versato (riduce il capitale finanziato)
     * @param tanAnnuo        Tasso Annuo Nominale del finanziamento (es. 0.0699 = 6.99%)
     * @param durataRateMesi  numero di rate mensili (es. 48, 60, 72, 84)
     * @param redditoNetto    reddito netto mensile del richiedente
     * @param kmMensili       chilometri percorsi al mese (0 = usa default 1500)
     * @return DTO con il dettaglio completo dei costi e il giudizio di sostenibilità
     */
    public RisultatoCalcoloDTO calcola(
            Motorizzazione motorizzazione,
            BigDecimal prezzoAuto,
            BigDecimal anticipo,
            BigDecimal tanAnnuo,
            int durataRateMesi,
            BigDecimal redditoNetto,
            int kmMensili
    ) {
        int km = kmMensili > 0 ? kmMensili : KM_MENSILI_DEFAULT;

        BigDecimal rata             = calcolaRataFrancese(prezzoAuto, anticipo, tanAnnuo, durataRateMesi);
        BigDecimal costoCarburante  = calcolaCostoCarburanteMensile(motorizzazione, km);
        BigDecimal costoPneumatici  = calcolaCostoPneumaticiMensile(motorizzazione);
        BigDecimal costoTagliandi   = calcolaCostoTagliandiMensile(motorizzazione, km);
        BigDecimal bolloMensile     = calcolaBolloMensile(motorizzazione);
        BigDecimal assicurazioneMensile = stimaAssicurazioneMensile(motorizzazione);

        BigDecimal totaleMensile = rata
                .add(costoCarburante)
                .add(costoPneumatici)
                .add(costoTagliandi)
                .add(bolloMensile)
                .add(assicurazioneMensile);

        // Regola standard: auto sostenibile se totale <= 30% del reddito netto
        BigDecimal percentualeReddito = totaleMensile
                .divide(redditoNetto, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        boolean sostenibile = percentualeReddito.compareTo(new BigDecimal("30.00")) <= 0;

        return RisultatoCalcoloDTO.builder()
                .rata(rata)
                .costoCarburanteMensile(costoCarburante)
                .costoPneumaticiMensile(costoPneumatici)
                .costoTagliandiMensile(costoTagliandi)
                .bolloMensile(bolloMensile)
                .assicurazioneMensile(assicurazioneMensile)
                .totaleCostoMensile(totaleMensile)
                .redditoNetto(redditoNetto)
                .percentualeSuReddito(percentualeReddito)
                .sostenibile(sostenibile)
                .kmMensiliUsati(km)
                .build();
    }

    // ================================================================
    // RATA FRANCESE
    // R = P * [ i(1+i)^n ] / [ (1+i)^n - 1 ]
    // ================================================================
    private BigDecimal calcolaRataFrancese(
            BigDecimal prezzoAuto,
            BigDecimal anticipo,
            BigDecimal tanAnnuo,
            int durataRateMesi
    ) {
        BigDecimal capitale = prezzoAuto.subtract(anticipo);
        if (capitale.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // tasso mensile = TAN / 12
        BigDecimal tassoMensile = tanAnnuo.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        if (tassoMensile.compareTo(BigDecimal.ZERO) == 0) {
            // tasso zero: rata = capitale / n
            return capitale.divide(BigDecimal.valueOf(durataRateMesi), 2, RoundingMode.HALF_UP);
        }

        // (1 + i)^n con MathContext per precisione
        MathContext mc = new MathContext(15, RoundingMode.HALF_UP);
        BigDecimal unopiui = BigDecimal.ONE.add(tassoMensile);
        BigDecimal potenza = unopiui.pow(durataRateMesi, mc);

        // R = P * i * (1+i)^n / ((1+i)^n - 1)
        BigDecimal numeratore   = capitale.multiply(tassoMensile).multiply(potenza);
        BigDecimal denominatore = potenza.subtract(BigDecimal.ONE);

        return numeratore.divide(denominatore, 2, RoundingMode.HALF_UP);
    }

    // ================================================================
    // COSTO CARBURANTE MENSILE
    // ================================================================
    private BigDecimal calcolaCostoCarburanteMensile(Motorizzazione m, int kmMensili) {
        TipoCarburante tipo = m.getTipoCarburante();

        // Elettrico puro: kWh/100km × prezzo kWh
        if (tipo == TipoCarburante.ELETTRICO) {
            BigDecimal consumoKwh = CONSUMO_EV_KWH_100KM;
            return consumoKwh
                    .multiply(BigDecimal.valueOf(kmMensili))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    .multiply(PREZZO_ELETTRICO)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // Ibridi plug-in: assume 60% km in elettrico, 40% in termico
        BigDecimal consumo = m.getConsumoMedioLitri100km() != null
                ? m.getConsumoMedioLitri100km()
                : new BigDecimal("8.00"); // fallback generico

        BigDecimal kmEffettivi;
        BigDecimal prezzoCarburante;

        switch (tipo) {
            case IBRIDO_PLUGIN -> {
                // 60% elettrico, 40% con consumo dichiarato
                kmEffettivi = BigDecimal.valueOf(kmMensili).multiply(new BigDecimal("0.40"));
                prezzoCarburante = PREZZO_BENZINA;
                BigDecimal costoTermico = consumo
                        .multiply(kmEffettivi)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                        .multiply(prezzoCarburante);
                BigDecimal costoElettrico = CONSUMO_EV_KWH_100KM
                        .multiply(BigDecimal.valueOf(kmMensili).multiply(new BigDecimal("0.60")))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                        .multiply(PREZZO_ELETTRICO);
                return costoTermico.add(costoElettrico).setScale(2, RoundingMode.HALF_UP);
            }
            case DIESEL, IBRIDO_DIESEL -> prezzoCarburante = PREZZO_DIESEL;
            case GPL                   -> prezzoCarburante = PREZZO_GPL;
            case METANO                -> prezzoCarburante = PREZZO_METANO;
            default                    -> prezzoCarburante = PREZZO_BENZINA; // BENZINA, IBRIDO_BENZINA, IDROGENO
        }

        return consumo
                .multiply(BigDecimal.valueOf(kmMensili))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .multiply(prezzoCarburante)
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ================================================================
    // COSTO PNEUMATICI MENSILE
    // Costo set / durata stimata in mesi
    // ================================================================
    private BigDecimal calcolaCostoPneumaticiMensile(Motorizzazione m) {
        BigDecimal costoSet = Boolean.TRUE.equals(m.getRunFlat())
                ? COSTO_PNEUMATICI_RUNFLAT
                : COSTO_PNEUMATICI_STANDARD;
        return costoSet.divide(BigDecimal.valueOf(DURATA_PNEUMATICI_MESI), 2, RoundingMode.HALF_UP);
    }

    // ================================================================
    // COSTO TAGLIANDI MENSILE
    // Media ponderata tra tagliando base e maior in base agli intervalli
    // ================================================================
    private BigDecimal calcolaCostoTagliandiMensile(Motorizzazione m, int kmMensili) {
        BigDecimal costoBase  = m.getCostoTagliandoBaseEur()  != null ? m.getCostoTagliandoBaseEur()  : new BigDecimal("250.00");
        BigDecimal costoMaior = m.getCostoTagliandoMaiorEur() != null ? m.getCostoTagliandoMaiorEur() : new BigDecimal("600.00");
        int kmBase            = m.getIntervalloTagliandoKm()       != null ? m.getIntervalloTagliandoKm()       : 15000;
        int kmMaior           = m.getIntervalloTagliandoMaiorKm()  != null ? m.getIntervalloTagliandoMaiorKm()  : 60000;

        // Costo annuo = (costoBase × nTagliandiBase) + (costoMaior × nTagliandiMaior)
        // nTagliandiBase al netto dei maior = (kmMaior/kmBase - 1) tagliandi ordinari + 1 maior ogni kmMaior
        int kmAnnui = kmMensili * 12;

        // Quanti tagliandi base per anno (incluso 1 che diventa maior)
        double tagliandiBaseAnno  = (double) kmAnnui / kmBase;
        // Quanti tagliandi maior per anno
        double tagliandiMaiorAnno = (double) kmAnnui / kmMaior;
        // Tagliandi ordinari = totale base - quelli che sono maior
        double tagliandiOrdAnno   = tagliandiBaseAnno - tagliandiMaiorAnno;

        BigDecimal costoAnnuo = costoBase.multiply(BigDecimal.valueOf(tagliandiOrdAnno))
                .add(costoMaior.multiply(BigDecimal.valueOf(tagliandiMaiorAnno)));

        return costoAnnuo.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    // ================================================================
    // BOLLO ACI MENSILE
    // Tariffe 2024: 2.58 €/kW (primi 100 kW) + 3.87 €/kW (oltre 100 kW)
    // Diesel: maggiorazione 0.26/0.39 €/kW
    // Elettrici: esenti
    // ================================================================
    private BigDecimal calcolaBolloMensile(Motorizzazione m) {
        TipoCarburante tipo = m.getTipoCarburante();
        int kw = m.getPotenzaKw() != null ? m.getPotenzaKw() : 70;

        // Elettrici puri: esenti (trattiamo come 0)
        if (tipo == TipoCarburante.ELETTRICO) {
            return BigDecimal.ZERO;
        }

        BigDecimal baseKw  = BOLLO_BASE_PER_KW;
        BigDecimal extraKw = BOLLO_EXTRA_PER_KW;

        // Maggiorazione diesel
        if (tipo == TipoCarburante.DIESEL || tipo == TipoCarburante.IBRIDO_DIESEL) {
            baseKw  = baseKw.add(BOLLO_DIESEL_BASE);
            extraKw = extraKw.add(BOLLO_DIESEL_EXTRA);
        }

        BigDecimal bolloAnnuo;
        if (kw <= BOLLO_SOGLIA_KW) {
            bolloAnnuo = baseKw.multiply(BigDecimal.valueOf(kw));
        } else {
            BigDecimal parteBassa = baseKw.multiply(BigDecimal.valueOf(BOLLO_SOGLIA_KW));
            BigDecimal parteAlta  = extraKw.multiply(BigDecimal.valueOf(kw - BOLLO_SOGLIA_KW));
            bolloAnnuo = parteBassa.add(parteAlta);
        }

        // Minimo bollo
        if (bolloAnnuo.compareTo(BOLLO_MINIMO) < 0) {
            bolloAnnuo = BOLLO_MINIMO;
        }

        return bolloAnnuo.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    // ================================================================
    // STIMA ASSICURAZIONE RC MENSILE
    // Basata sul gruppo assicurativo (1-20)
    // Fascia media per un guidatore con 5+ anni di esperienza, classe 14 BM
    // ================================================================
    private BigDecimal stimaAssicurazioneMensile(Motorizzazione m) {
        int gruppo = m.getGruppoAssicurativo() != null ? m.getGruppoAssicurativo() : 10;
        gruppo = Math.max(1, Math.min(20, gruppo)); // clamp 1-20

        // Stima annua: da ~400€ (gruppo 1) a ~1800€ (gruppo 20)
        // Interpolazione lineare: 400 + (gruppo - 1) * (1400 / 19)
        double stimaAnnua = 400.0 + (gruppo - 1) * (1400.0 / 19.0);

        return BigDecimal.valueOf(stimaAnnua)
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }
}

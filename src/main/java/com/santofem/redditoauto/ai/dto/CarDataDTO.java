package com.santofem.redditoauto.ai.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.langchain4j.model.output.structured.Description;

/**
 * DTO fortemente tipizzato restituito da Gemini tramite LangChain4j structured output.
 *
 * COME FUNZIONA:
 * LangChain4j legge le annotazioni @Description (a livello record) e
 * @JsonPropertyDescription (a livello campo) per costruire il JSON Schema
 * che viene inviato a Gemini come istruzione sul formato di risposta atteso.
 * Gemini restituisce un JSON che LangChain4j deserializza in questo record.
 *
 * REGOLA ANTI-ALLUCINAZIONE:
 * Tutti i campi sono nullable. Se il dato non e' nel testo grezzo,
 * Gemini deve restituire null — mai inventare o stimare.
 */
@Description("Dati tecnici estratti dalla scheda tecnica di un autoveicolo. " +
             "Tutti i campi non trovati nel testo devono essere null.")
public record CarDataDTO(

    @JsonPropertyDescription("Marca del veicolo, es. 'Volkswagen', 'BMW', 'Toyota'")
    String marca,

    @JsonPropertyDescription("Modello del veicolo, es. 'Golf', 'Serie 3', 'Yaris'")
    String modello,

    @JsonPropertyDescription("Nome completo della motorizzazione inclusa potenza e cambio, es. '2.0 TDI 150 CV DSG'")
    String nomeMotore,

    @JsonPropertyDescription("Anno di produzione o immatricolazione come intero, es. 2022")
    Integer annoProduzione,

    @JsonPropertyDescription(
        "Tipo carburante. Usa ESATTAMENTE uno di questi valori: " +
        "BENZINA, DIESEL, GPL, METANO, IBRIDO_BENZINA, IBRIDO_DIESEL, IBRIDO_PLUGIN, ELETTRICO, IDROGENO. " +
        "null se non presente nel testo.")
    String tipoCarburante,

    @JsonPropertyDescription(
        "Tipo cambio. Usa ESATTAMENTE uno di questi valori: " +
        "MANUALE, AUTOMATICO_TRADIZIONALE, DCT, CVT, SINGOLA_MARCIA. " +
        "null se non presente nel testo.")
    String tipoCambio,

    @JsonPropertyDescription("Potenza motore in Kilowatt come intero, es. 110. null se assente.")
    Integer potenzaKw,

    @JsonPropertyDescription("Potenza motore in Cavalli Vapore come intero, es. 150. null se assente.")
    Integer potenzaCv,

    @JsonPropertyDescription("Cilindrata in centimetri cubici come intero, es. 1968. null se assente.")
    Integer cilindrataCC,

    @JsonPropertyDescription(
        "Consumo medio ciclo combinato WLTP in litri per 100km, es. 5.5. " +
        "Se il testo riporta km/l, converti: consumo = 100 / (km/l). null se assente.")
    Double consumoMedioLitri100km,

    @JsonPropertyDescription("Consumo ciclo urbano in litri per 100km, es. 7.2. null se assente.")
    Double consumoUrbanoLitri100km,

    @JsonPropertyDescription("Consumo ciclo extraurbano in litri per 100km, es. 4.5. null se assente.")
    Double consumoExtraurbanoLitri100km,

    @JsonPropertyDescription("Autonomia elettrica in km (solo per EV o PHEV plug-in), es. 60. null per tutti gli altri.")
    Integer autonomiaKmElettrica,

    @JsonPropertyDescription(
        "Misura pneumatici anteriori nel formato standard LARGHEZZA/PROFILO RZEPPA, es. '205/55 R16'. " +
        "null se assente.")
    String misuraPneumaticiAnteriori,

    @JsonPropertyDescription(
        "Misura pneumatici posteriori se diversa dagli anteriori, es. '225/45 R17'. " +
        "null se uguale agli anteriori o se assente.")
    String misuraPneumaticiPosteriori,

    @JsonPropertyDescription("true se i pneumatici sono di tipo run-flat, false altrimenti. null se non specificato.")
    Boolean runFlat,

    @JsonPropertyDescription("Prezzo di listino ufficiale in euro come decimale, es. 32500.0. null se assente.")
    Double prezzoListinoEur,

    @JsonPropertyDescription("Costo stimato tagliando ordinario (cambio olio + filtri) in euro, es. 200.0. null se assente.")
    Double costoTagliandoBaseEur,

    @JsonPropertyDescription("Costo stimato tagliando maggiore (include cinghia distribuzione se presente) in euro, es. 650.0. null se assente.")
    Double costoTagliandoMaiorEur,

    @JsonPropertyDescription("Intervallo tra tagliandi ordinari in km, es. 15000. null se assente.")
    Integer intervalloTagliandoKm,

    @JsonPropertyDescription("Intervallo tra tagliandi maggiori in km, es. 60000. null se assente.")
    Integer intervalloTagliandoMaiorKm,

    @JsonPropertyDescription("Gruppo assicurativo da 1 (rischio minimo) a 20 (rischio massimo). null se assente.")
    Integer gruppoAssicurativo

) {
    /**
     * Verifica che i campi obbligatori per il calcolo di sostenibilita' siano presenti.
     *
     * DIFESA CONTRO STRINGA "null":
     * LangChain4j / Gemini in certi casi deserializza i campi assenti come la
     * stringa letterale "null" invece di un vero null Java. isPresente() copre
     * entrambi i casi.
     *
     * @return true se i dati minimi sono presenti e utilizzabili
     */
    public boolean isValid() {
        return isPresente(marca)
            && isPresente(modello)
            && isPresente(nomeMotore)
            && potenzaKw != null && potenzaKw > 0
            && isPresente(tipoCarburante);
    }

    /**
     * Ritorna true se la stringa e' non-null, non-blank e non la stringa letterale "null".
     * Difesa contro l'output "null" (stringa) che Gemini/LangChain4j puo' restituire
     * al posto di un vero null Java.
     */
    private static boolean isPresente(String s) {
        return s != null && !s.isBlank() && !s.equalsIgnoreCase("null");
    }
}

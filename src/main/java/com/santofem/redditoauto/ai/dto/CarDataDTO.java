package com.santofem.redditoauto.ai.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * DTO fortemente tipizzato restituito dall'AI (LangChain4j + Gemini).
 * Usato come output dell'AiCarDataExtractor.
 * Tutti i campi sono nullable: se il dato non è nel testo grezzo, Gemini restituisce null.
 *
 * Nota: record Java — immutabile per design, compatibile nativo con LangChain4j structured output.
 */
public record CarDataDTO(

    @JsonPropertyDescription("Marca del veicolo, es. 'Volkswagen', 'BMW'")
    String marca,

    @JsonPropertyDescription("Modello del veicolo, es. 'Golf', 'Serie 3'")
    String modello,

    @JsonPropertyDescription("Nome completo della motorizzazione, es. '2.0 TDI 150 CV DSG'")
    String nomeMotore,

    @JsonPropertyDescription("Anno di produzione o immatricolazione, numero intero es. 2022")
    Integer annoProduzione,

    @JsonPropertyDescription("Tipo carburante: BENZINA, DIESEL, GPL, METANO, IBRIDO_BENZINA, IBRIDO_DIESEL, IBRIDO_PLUGIN, ELETTRICO")
    String tipoCarburante,

    @JsonPropertyDescription("Tipo cambio: MANUALE, AUTOMATICO_TRADIZIONALE, DCT, CVT, SINGOLA_MARCIA")
    String tipoCambio,

    @JsonPropertyDescription("Potenza in Kilowatt, numero intero es. 110")
    Integer potenzaKw,

    @JsonPropertyDescription("Potenza in Cavalli Vapore, numero intero es. 150")
    Integer potenzaCv,

    @JsonPropertyDescription("Cilindrata in cc, numero intero es. 1968")
    Integer cilindrataCC,

    @JsonPropertyDescription("Consumo medio in litri per 100km (ciclo combinato), es. 5.5")
    Double consumoMedioLitri100km,

    @JsonPropertyDescription("Consumo in ciclo urbano in litri per 100km, es. 7.2")
    Double consumoUrbanoLitri100km,

    @JsonPropertyDescription("Consumo in ciclo extraurbano in litri per 100km, es. 4.5")
    Double consumoExtraurbanoLitri100km,

    @JsonPropertyDescription("Autonomia elettrica in km (solo EV o PHEV), es. 60")
    Integer autonomiaKmElettrica,

    @JsonPropertyDescription("Misura pneumatici anteriori nel formato standard, es. '205/55 R16'")
    String misuraPneumaticiAnteriori,

    @JsonPropertyDescription("Misura pneumatici posteriori se diversa dagli anteriori, es. '225/45 R17'")
    String misuraPneumaticiPosteriori,

    @JsonPropertyDescription("true se i pneumatici sono run-flat, false altrimenti")
    Boolean runFlat,

    @JsonPropertyDescription("Prezzo di listino in euro, es. 32500.00")
    Double prezzoListinoEur,

    @JsonPropertyDescription("Costo stimato tagliando ordinario (olio+filtri) in euro, es. 200.0")
    Double costoTagliandoBaseEur,

    @JsonPropertyDescription("Costo stimato tagliando maggiore (cinghia distribuzione inclusa) in euro, es. 650.0")
    Double costoTagliandoMaiorEur,

    @JsonPropertyDescription("Intervallo tagliando ordinario in km, es. 15000")
    Integer intervalloTagliandoKm,

    @JsonPropertyDescription("Intervallo tagliando maggiore in km, es. 60000")
    Integer intervalloTagliandoMaiorKm,

    @JsonPropertyDescription("Gruppo assicurativo stimato (1-20), dove 1 è meno rischioso")
    Integer gruppoAssicurativo

) {}

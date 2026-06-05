package com.santofem.redditoauto.acquisition;

/**
 * Contratto per qualsiasi fonte di dati tecnici auto.
 * Ogni implementazione incapsula la logica di accesso a una fonte specifica
 * (Jsoup, REST API, CSV locale, ecc.) senza esporre i dettagli al chiamante.
 */
public interface CarSourceAdapter {

    /** Identificatore leggibile della fonte (usato nei log e come source nei candidati). */
    String sourceName();

    /**
     * Verifica se questo adapter è in grado di gestire la richiesta.
     * Usato dallo ScrapingOrchestrator per filtrare gli adapter disponibili.
     */
    boolean supports(CarLookupRequest request);

    /**
     * Esegue l'estrazione e restituisce i candidati per campo.
     * Non deve mai lanciare eccezioni: gli errori vanno catturati internamente
     * e restituiti come {@link SourceExtractionResult#failure}.
     */
    SourceExtractionResult extract(CarLookupRequest request);
}

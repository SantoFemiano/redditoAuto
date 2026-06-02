package com.santofem.redditoauto.scraper;

import java.util.Optional;

/**
 * Contratto per il servizio di web scraping.
 */
public interface WebScraper {

    /**
     * Cerca informazioni tecniche tramite navigazione a cascata per marca/modello/motore/anno.
     */
    Optional<String> scrape(String marca, String modello, String motore, int anno);

    /**
     * Variante che restituisce uno {@link ScraperResult} arricchito con anno effettivo
     * e flag di fallback. Usata dall'orchestratore per il motor-scoring.
     */
    ScraperResult scrapeConRisultato(String marca, String modello, String motore,
                                     int anno, int potenzaCv,
                                     String tipoCarburante, String tipoCambio);

    /**
     * Scarica e parsa un URL diretto (usato da /estrai-url).
     */
    Optional<String> scrapeUrl(String url);
}

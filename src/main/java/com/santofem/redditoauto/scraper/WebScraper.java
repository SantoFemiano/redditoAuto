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
     * Scarica e parsa un URL diretto (usato da /estrai-url).
     */
    Optional<String> scrapeUrl(String url);
}

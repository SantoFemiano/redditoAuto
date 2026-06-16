package com.santofem.redditoauto.scraper;

/**
 * Interfaccia Strategy per scraper specializzati per sito.
 *
 * Ogni implementazione conosce la struttura HTML di un sito specifico
 * e sa come estrarne le informazioni più rilevanti (dati tecnici, prezzo, ecc.).
 *
 * Il dispatcher {@link UrlScraperDispatcher} seleziona automaticamente
 * l'implementazione corretta in base al dominio dell'URL fornito.
 */
public interface UrlScraperStrategy {

    /**
     * Indica se questa strategia supporta l'URL fornito.
     *
     * @param url URL da verificare
     * @return true se il sito è gestito da questa strategia
     */
    boolean supports(String url);

    /**
     * Esegue lo scraping dell'URL e restituisce un risultato arricchito
     * con il testo tecnico, il prezzo eventuale e gli hint sull'identità del veicolo.
     *
     * @param url URL della pagina del modello/scheda tecnica
     * @return {@link MultiSiteScraperResult} con tutti i dati estratti
     */
    MultiSiteScraperResult scrape(String url);

    /**
     * Nome leggibile del sito (per log e fonteDati).
     */
    String siteName();
}

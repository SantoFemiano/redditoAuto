package com.santofem.redditoauto.scraper;

import java.util.Optional;

/**
 * Contratto per il servizio di web scraping.
 *
 * Accetta i parametri di identificazione di un'auto e restituisce
 * il testo grezzo estratto dal web, pronto per essere passato all'AI.
 *
 * Il risultato e' un Optional<String>: vuoto se nessuna fonte ha risposto.
 */
public interface WebScraper {

    /**
     * Cerca informazioni tecniche sul veicolo specificato.
     *
     * @param marca    es. "Volkswagen"
     * @param modello  es. "Golf"
     * @param motore   es. "2.0 TDI 150 CV DSG"
     * @param anno     es. 2022
     * @return testo grezzo pulito (HTML stripped), o Optional.empty() se fallisce
     */
    Optional<String> scrape(String marca, String modello, String motore, int anno);
}

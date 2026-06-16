package com.santofem.redditoauto.scraper;

import com.santofem.redditoauto.scraper.sites.AutoDataNetUrlScraper;
import com.santofem.redditoauto.scraper.sites.AutoItScraper;
import com.santofem.redditoauto.scraper.sites.AutoScout24Scraper;
import com.santofem.redditoauto.scraper.sites.AutomotoItScraper;
import com.santofem.redditoauto.scraper.sites.InfomotoriScraper;
import com.santofem.redditoauto.scraper.sites.MotoristItalyScraper;
import com.santofem.redditoauto.scraper.sites.QuattroruotesScraper;
import com.santofem.redditoauto.scraper.sites.SubitoItScraper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Dispatcher che riconosce il sito dall'URL e delega allo scraper specializzato corretto.
 *
 * Siti supportati:
 * - auto-data.net         → scheda tecnica completa
 * - autoscout24.it / .com → prezzo listino/usato + dati tecnici
 * - motorist.it           → scheda tecnica italiana + prezzo
 * - quattroruote.it       → scheda tecnica + prezzo listino ufficiale
 * - automoto.it           → scheda tecnica + prezzo listino
 * - infomotori.com        → scheda tecnica + prezzo listino
 * - auto.it               → scheda tecnica + prezzo listino (con JSON-LD)
 * - subito.it             → prezzo mercato usato
 *
 * Per siti non riconosciuti usa il fallback generico Jsoup.
 *
 * L'ordine della lista strategies è significativo: i siti più specifici
 * (e con selettori più precisi) sono elencati prima.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UrlScraperDispatcher {

    private final AutoDataNetUrlScraper  autoDataNetUrlScraper;
    private final AutoScout24Scraper     autoScout24Scraper;
    private final MotoristItalyScraper   motoristItalyScraper;
    private final QuattroruotesScraper   quattroruotesScraper;
    private final AutomotoItScraper      automotoItScraper;
    private final InfomotoriScraper      infomotoriScraper;
    private final AutoItScraper          autoItScraper;
    private final SubitoItScraper        subitoItScraper;
    private final GenericUrlScraper      genericUrlScraper;

    /**
     * Riconosce il sito e dispatcha allo scraper appropriato.
     * Usa il pattern Stream per evitare if-else a cascata.
     *
     * @param url URL da scrapare
     * @return risultato arricchito multi-sito
     */
    public MultiSiteScraperResult dispatch(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL non può essere vuoto");
        }

        String urlLower = url.toLowerCase();
        log.info("[Dispatcher] Analisi URL: {}", url);

        // Ordine per specificità: i siti con struttura HTML più definita prima.
        // auto.it deve stare dopo automoto.it e autoscout24 per evitare false catture
        // (tutti i .it contengono "auto.it" come sottostringa).
        List<UrlScraperStrategy> strategies = List.of(
                autoDataNetUrlScraper,
                autoScout24Scraper,
                motoristItalyScraper,
                quattroruotesScraper,
                automotoItScraper,
                infomotoriScraper,
                subitoItScraper,
                autoItScraper  // ultimo tra gli specializzati: supports() più generico
        );

        return strategies.stream()
                .filter(s -> s.supports(urlLower))
                .findFirst()
                .map(s -> {
                    log.info("[Dispatcher] Sito riconosciuto: {} → {}", s.siteName(), url);
                    return s.scrape(url);
                })
                .orElseGet(() -> {
                    log.info("[Dispatcher] Sito non riconosciuto, uso scraper generico per: {}", url);
                    return genericUrlScraper.scrape(url);
                });
    }
}

package com.santofem.redditoauto.scraper;

import com.santofem.redditoauto.scraper.sites.AutoDataNetUrlScraper;
import com.santofem.redditoauto.scraper.sites.AutoScout24Scraper;
import com.santofem.redditoauto.scraper.sites.MotoristItalyScraper;
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
 * - motorist.it / auto.it / autoblog.it → schede tecniche italiane
 * - subito.it             → prezzo mercato usato
 *
 * Per siti non riconosciuti usa il fallback generico Jsoup.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UrlScraperDispatcher {

    private final AutoDataNetUrlScraper autoDataNetUrlScraper;
    private final AutoScout24Scraper autoScout24Scraper;
    private final MotoristItalyScraper motoristItalyScraper;
    private final SubitoItScraper subitoItScraper;
    private final GenericUrlScraper genericUrlScraper;

    /**
     * Riconosce il sito e dispatcha allo scraper appropriato.
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

        List<UrlScraperStrategy> strategies = List.of(
                autoDataNetUrlScraper,
                autoScout24Scraper,
                motoristItalyScraper,
                subitoItScraper
        );

        for (UrlScraperStrategy strategy : strategies) {
            if (strategy.supports(urlLower)) {
                log.info("[Dispatcher] Sito riconosciuto: {} → {}", url, strategy.siteName());
                return strategy.scrape(url);
            }
        }

        log.info("[Dispatcher] Sito non riconosciuto, uso scraper generico per: {}", url);
        return genericUrlScraper.scrape(url);
    }
}

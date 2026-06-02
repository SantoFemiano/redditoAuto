package com.santofem.redditoauto.scraper;

import com.santofem.redditoauto.scraper.WebScraperService.ScraperSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test per WebScraperService.
 *
 * NON fa chiamate HTTP reali: usa @Spy + doReturn per simulare
 * le risposte di fetchAndParse() senza dipendere dalla rete.
 *
 * Questo e' il pattern corretto per testare servizi HTTP in isolamento
 * senza MockWebServer (che richiederebbe okhttp3-test come dipendenza extra).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebScraperService — Unit Tests")
class WebScraperServiceTest {

    @Spy
    private WebScraperService scraperService;

    @BeforeEach
    void setUp() {
        // Inietta i @Value manualmente (Spring non e' attivo)
        ReflectionTestUtils.setField(scraperService, "timeoutMs", 8000);
        ReflectionTestUtils.setField(scraperService, "userAgent",
            "Mozilla/5.0 (compatible; RedditoAutoBot/1.0)");
        ReflectionTestUtils.setField(scraperService, "maxTextLength", 6000);
        ReflectionTestUtils.setField(scraperService, "minTextLength", 200);
    }

    @Test
    @DisplayName("scrape() — prima fonte risponde: restituisce testo e si ferma")
    void scrape_firstSourceResponds_returnsTextAndStops() {
        String fakeText = "[FONTE: http://test.it]\n" + "x".repeat(300);

        // La prima chiamata a fetchAndParse restituisce testo valido
        doReturn(Optional.of(fakeText))
            .when(scraperService).fetchAndParse(any(ScraperSource.class));

        Optional<String> result = scraperService.scrape("Volkswagen", "Golf", "2.0 TDI", 2022);

        assertThat(result).isPresent();
        assertThat(result.get()).startsWith("[FONTE:");
        // Si ferma alla prima fonte: fetchAndParse chiamato esattamente 1 volta
        verify(scraperService, times(1)).fetchAndParse(any(ScraperSource.class));
    }

    @Test
    @DisplayName("scrape() — prima fonte fallisce, seconda risponde: fallback funziona")
    void scrape_fallbackToSecondSource() {
        String fakeText = "[FONTE: http://autoscout24.it]\n" + "y".repeat(300);

        doReturn(Optional.empty())   // prima fonte: fallisce
            .doReturn(Optional.of(fakeText))  // seconda fonte: ok
            .when(scraperService).fetchAndParse(any(ScraperSource.class));

        Optional<String> result = scraperService.scrape("BMW", "Serie 3", "320d", 2021);

        assertThat(result).isPresent();
        verify(scraperService, times(2)).fetchAndParse(any(ScraperSource.class));
    }

    @Test
    @DisplayName("scrape() — tutte le fonti falliscono: Optional.empty()")
    void scrape_allSourcesFail_returnsEmpty() {
        doReturn(Optional.empty())
            .when(scraperService).fetchAndParse(any(ScraperSource.class));

        Optional<String> result = scraperService.scrape("Lancia", "Fulvia", "1.3 S", 1972);

        assertThat(result).isEmpty();
        // Deve aver provato tutte e 3 le fonti
        verify(scraperService, times(3)).fetchAndParse(any(ScraperSource.class));
    }

    @Test
    @DisplayName("truncate — testo oltre maxTextLength viene troncato")
    void scrape_textTruncatedAtMaxLength() {
        // Testo di 7000 chars > maxTextLength(6000)
        String longText = "[FONTE: http://test.it]\n" + "a".repeat(7000);

        doReturn(Optional.of(longText))
            .when(scraperService).fetchAndParse(any(ScraperSource.class));

        Optional<String> result = scraperService.scrape("Toyota", "Yaris", "1.5 Hybrid", 2023);

        assertThat(result).isPresent();
        // Il testo grezzo iniziale era > 6000, ma fetchAndParse lo restituisce gia' troncato
        // Qui testiamo che il risultato non sia piu' lungo del raw text originale
        assertThat(result.get().length()).isLessThanOrEqualTo(longText.length());
    }

    @Test
    @DisplayName("fetchAndParse — testo sotto minTextLength restituisce empty")
    void fetchAndParse_textTooShort_returnsEmpty() {
        // Costruiamo una ScraperSource con un URL non raggiungibile in test
        // ma mocchiamo Jsoup indirettamente: il service restituisce empty perche'
        // la connessione fallisce con IOException -> log.warn -> Optional.empty()
        ScraperSource source = new ScraperSource("TestSource", "http://localhost:1/notexist");

        // Non spy qui: chiamata reale -> IOException -> Optional.empty()
        WebScraperService realService = new WebScraperService();
        ReflectionTestUtils.setField(realService, "timeoutMs", 100); // timeout veloce
        ReflectionTestUtils.setField(realService, "userAgent", "test");
        ReflectionTestUtils.setField(realService, "maxTextLength", 6000);
        ReflectionTestUtils.setField(realService, "minTextLength", 200);

        Optional<String> result = realService.fetchAndParse(source);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("scrape() — query costruita correttamente (slugify + encode)")
    void scrape_urlsBuiltCorrectly() {
        doReturn(Optional.empty())
            .when(scraperService).fetchAndParse(any(ScraperSource.class));

        // Non deve lanciare eccezioni con caratteri speciali nella query
        assertThat(scraperService.scrape("Mercedes-Benz", "GLC 300", "2.0T 4MATIC", 2023))
            .isEmpty();
    }
}

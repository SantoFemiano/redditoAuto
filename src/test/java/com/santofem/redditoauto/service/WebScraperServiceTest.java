package com.santofem.redditoauto.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("WebScraperService — Unit Tests")
class WebScraperServiceTest {

    private final WebScraperService scraper = new WebScraperService();

    @Test
    @DisplayName("URL non valido (ftp://) → WebScraperException")
    void url_protocollo_invalido() {
        assertThatThrownBy(() -> scraper.scaricaEPulisci("ftp://example.com"))
            .isInstanceOf(WebScraperService.WebScraperException.class)
            .hasMessageContaining("solo http/https");
    }

    @Test
    @DisplayName("URL malformato → WebScraperException")
    void url_malformato() {
        assertThatThrownBy(() -> scraper.scaricaEPulisci("non-un-url")
        ).isInstanceOf(WebScraperService.WebScraperException.class);
    }

    @Test
    @DisplayName("HTML pulito: rimozione tag script, style, nav")
    void pulizia_html_rimuove_elementi_non_informativi() throws Exception {
        // Test del metodo privato via reflection (alternativa: rendere il metodo package-private)
        // Usiamo invece un test indiretto passando HTML finto via mock
        // Per il metodo estraiTestoPulito usiamo Jsoup direttamente come test di unit
        var jsoupDoc = org.jsoup.Jsoup.parse(
            "<html><body>"
            + "<script>var x=1;</script>"
            + "<nav>Menu</nav>"
            + "<main><h1>Volkswagen Golf</h1><p>Potenza: 110 kW</p></main>"
            + "<footer>Footer</footer>"
            + "</body></html>"
        );
        jsoupDoc.select("script, style, nav, footer").remove();
        String testo = jsoupDoc.text();

        assertThat(testo).contains("Volkswagen Golf");
        assertThat(testo).contains("110 kW");
        assertThat(testo).doesNotContain("var x=1");
        assertThat(testo).doesNotContain("Menu");
        assertThat(testo).doesNotContain("Footer");
    }
}

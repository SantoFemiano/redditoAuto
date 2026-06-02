package com.santofem.redditoauto.service;

import com.santofem.redditoauto.scraper.WebScraper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Test per WebScraper (interfaccia).
 * Verifica il contratto dell'interfaccia tramite mock.
 */
@ExtendWith(MockitoExtension.class)
class WebScraperServiceTest {

    @Mock
    WebScraper webScraper;

    @Test
    @DisplayName("scrape() restituisce Optional.empty() se nessun risultato trovato")
    void scrape_nessunaFonte() {
        when(webScraper.scrape("MarcaSconosciuta", "ModelloX", "MotoreY", 2020))
            .thenReturn(Optional.empty());

        Optional<String> result = webScraper.scrape("MarcaSconosciuta", "ModelloX", "MotoreY", 2020);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("scrape() restituisce Optional con testo se fonte trovata")
    void scrape_fontePresente() {
        String testoAtteso = "Volkswagen Golf 2.0 TDI 150 CV potenza 110 kW consumo 5.5 l/100km";
        when(webScraper.scrape("Volkswagen", "Golf", "2.0 TDI", 2022))
            .thenReturn(Optional.of(testoAtteso));

        Optional<String> result = webScraper.scrape("Volkswagen", "Golf", "2.0 TDI", 2022);

        assertThat(result).isPresent().contains(testoAtteso);
    }
}

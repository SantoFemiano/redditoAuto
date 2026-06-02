package com.santofem.redditoauto.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Service che scarica il contenuto grezzo di una pagina web
 * e lo pulisce da HTML/JS/CSS prima di passarlo all'AI.
 *
 * Usa Java 17 HttpClient nativo (no dipendenze extra) + Jsoup per il parsing HTML.
 * L'output è testo plain pronto per AiCarDataExtractor.
 */
@Service
@Slf4j
public class WebScraperService {

    private static final int    TIMEOUT_SECONDI  = 15;
    private static final int    MAX_CHARS_OUTPUT  = 12_000; // Limite per non sprecare token Gemini
    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private final HttpClient httpClient;

    public WebScraperService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDI))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    /**
     * Scarica una pagina web e restituisce il testo pulito (no HTML/JS/CSS).
     * Il testo viene troncato a MAX_CHARS_OUTPUT per ottimizzare i token AI.
     *
     * @param url URL della scheda tecnica (es. AutoScout24, Motorionline, MotorTrend)
     * @return Testo grezzo pronto per l'estrazione AI
     * @throws WebScraperException se la pagina non è raggiungibile o il contenuto è vuoto
     */
    public String scaricaEPulisci(String url) {
        log.info("Scraping URL: {}", url);
        validaUrl(url);

        String html = scaricaHtml(url);
        String testo = estraiTestoPulito(html);

        if (testo.isBlank()) {
            throw new WebScraperException(
                "Nessun contenuto testuale estratto da: " + url +
                ". La pagina potrebbe richiedere JavaScript (SPA).");
        }

        String risultato = testo.length() > MAX_CHARS_OUTPUT
            ? testo.substring(0, MAX_CHARS_OUTPUT)
            : testo;

        log.info("Scraping completato: {} caratteri estratti da {}", risultato.length(), url);
        return risultato;
    }

    /**
     * Variante con fallback: se lo scraping del primo URL fallisce, prova il secondo.
     * Utile per avere AutoScout24 come primario e Motorionline come fallback.
     */
    public String scaricaEPulisciConFallback(String urlPrimario, String urlFallback) {
        try {
            return scaricaEPulisci(urlPrimario);
        } catch (WebScraperException e) {
            log.warn("Scraping primario fallito ({}), tento fallback: {}", e.getMessage(), urlFallback);
            return scaricaEPulisci(urlFallback);
        }
    }

    // -----------------------------------------------
    // METODI PRIVATI
    // -----------------------------------------------

    private void validaUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
                throw new WebScraperException("URL non valido (solo http/https): " + url);
            }
        } catch (IllegalArgumentException e) {
            throw new WebScraperException("URL malformato: " + url);
        }
    }

    private String scaricaHtml(String url) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDI))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.5")
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new WebScraperException("Errore di rete per URL: " + url + " - " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebScraperException("Scraping interrotto per: " + url);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new WebScraperException(
                "HTTP " + response.statusCode() + " per URL: " + url);
        }

        return response.body();
    }

    /**
     * Pulisce l'HTML mantenendo solo il testo leggibile.
     * Rimuove: script, style, nav, footer, cookie banner, pubblicità.
     * Normalizza spazi multipli e righe vuote eccessive.
     */
    private String estraiTestoPulito(String html) {
        Document doc = Jsoup.parse(html);

        // Rimuove elementi non informativi
        doc.select("script, style, nav, footer, header, iframe, noscript, "
            + ".cookie-banner, .ad, .advertisement, .sidebar, .menu, "
            + "[class*='cookie'], [class*='banner'], [id*='cookie']").remove();

        // Usa Jsoup per estrarre testo plain mantenendo newline significativi
        String testo = Jsoup.clean(
            doc.select("body").html(),
            "",
            Safelist.none(),
            new Document.OutputSettings().prettyPrint(false)
        );

        // Normalizza: rimuove righe vuote eccessive e spazi multipli
        return testo
            .replaceAll("[ \t]+", " ")
            .replaceAll("\\n{3,}", "\n\n")
            .replaceAll("&nbsp;", " ")
            .replaceAll("&amp;", "&")
            .replaceAll("&lt;", "<")
            .replaceAll("&gt;", ">")
            .trim();
    }

    // -----------------------------------------------
    // ECCEZIONE DEDICATA
    // -----------------------------------------------

    public static class WebScraperException extends RuntimeException {
        public WebScraperException(String message) {
            super(message);
        }
    }
}

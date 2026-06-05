package com.santofem.redditoauto.acquisition;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;

/**
 * Base class per tutti gli adapter che usano Jsoup per il fetch HTTP.
 * Centralizza timeout, user-agent, retry-logic e validazione risposta.
 * Jsoup è un dettaglio implementativo qui, non nel contratto dell'adapter.
 */
@Slf4j
public abstract class AbstractJsoupAdapter implements CarSourceAdapter {

    @Value("${scraper.timeout-ms:8000}")
    protected int timeoutMs;

    @Value("${scraper.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36}")
    protected String userAgent;

    /**
     * Fetch con Jsoup. Restituisce null in caso di errore
     * (il caller deve gestire il null come extraction failure).
     */
    protected Document fetchDocument(String url) {
        try {
            log.debug("[{}] GET {}", sourceName(), url);
            return Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout(timeoutMs)
                .ignoreHttpErrors(true)
                .followRedirects(true)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
                .get();
        } catch (IOException e) {
            log.warn("[{}] Errore HTTP su {}: {}", sourceName(), url, e.getMessage());
            return null;
        }
    }

    /** Sanitizza il testo grezzo per l'invio all'AI. */
    protected String sanitize(String text) {
        if (text == null) return "";
        return text
            .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "")
            .replace('"', '\'')
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
    }

    /** Tronca il testo per non superare il limite di output token di Gemini. */
    protected String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...[TRONCATO]";
    }
}

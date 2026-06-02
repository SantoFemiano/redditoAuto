package com.santofem.redditoauto.scraper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * WebScraperService - usa SOLO auto-data.net tramite AutoDataNetScraper.
 *
 * Non ci sono fallback su altri siti: auto-data.net è l'unica fonte.
 * Se lo scraping fallisce, l'orchestratore userà il fallback AI-direct.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WebScraperService implements WebScraper {

    private final AutoDataNetScraper autoDataNetScraper;

    @Value("${scraper.timeout-ms:8000}")
    private int timeoutMs;

    @Value("${scraper.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36}")
    private String userAgent;

    /**
     * MAX TESTO AI: 3500 chars.
     * Testi > 4000 chars aumentano la probabilità di JSON troncato da Gemini.
     * auto-data.net produce schede di ~1500-3000 chars: sufficiente.
     */
    @Value("${scraper.max-text-length:3500}")
    private int maxTextLength;

    @Value("${scraper.min-text-length:200}")
    private int minTextLength;

    private static final Set<String> KEYWORD_TECNICHE = Set.of(
        "l/100", "l/100km", " cc", " nm", "rpm", "euro 6", "euro6",
        "g/km", "co2", "kwh", "tdi", "tsi", "gdi", "crdi",
        "consumi", "cilindri", "valvole", "coppia massima", "kw",
        "cilindrata", "potenza", "carburante"
    );

    // -----------------------------------------------
    // PUBLIC API
    // -----------------------------------------------

    @Override
    public Optional<String> scrape(String marca, String modello, String motore, int anno) {
        log.info("[Scraper] Avvio ricerca per: {} {} {} {}", marca, modello, motore, anno);

        Optional<String> result = autoDataNetScraper.scrape(marca, modello, motore, anno);

        if (result.isPresent()) {
            String raw = result.get();
            String sanitized = sanitizeForAi(raw);
            String truncated = truncate(sanitized, maxTextLength);
            log.info("[Scraper] Testo tecnico trovato da 'auto-data.net' ({} chars)", truncated.length());
            return Optional.of(truncated);
        }

        log.warn("[Scraper] auto-data.net non ha trovato dati per: {} {} {}", marca, modello, motore);
        return Optional.empty();
    }

    /**
     * Variante arricchita che restituisce uno {@link ScraperResult} con anno effettivo
     * e flag di fallback. Delega direttamente ad AutoDataNetScraper.
     * Il sanitize/truncate sul testo viene lasciato all'orchestratore (che usa MAX_SCRAPING_CHARS).
     */
    @Override
    public ScraperResult scrapeConRisultato(String marca, String modello, String motore,
                                             int anno, int potenzaCv,
                                             String tipoCarburante, String tipoCambio) {
        log.info("[Scraper] Avvio ricerca per: {} {} {} {}", marca, modello, motore, anno);
        ScraperResult result = autoDataNetScraper.scrapeConRisultato(
            marca, modello, motore, anno, potenzaCv, tipoCarburante, tipoCambio);

        if (result.hasText()) {
            String sanitized = sanitizeForAi(result.testo());
            log.info("[Scraper] Testo tecnico trovato da 'auto-data.net' ({} chars)", sanitized.length());
            return result.withTesto(sanitized);
        }

        log.warn("[Scraper] auto-data.net non ha trovato dati per: {} {} {}", marca, modello, motore);
        return result;
    }

    @Override
    public Optional<String> scrapeUrl(String url) {
        log.info("[Scraper] Scraping URL diretto: {}", url);
        return fetchAndParse(new ScraperSource("url-diretto", url));
    }

    // -----------------------------------------------
    // FETCH GENERICO (usato solo da scrapeUrl)
    // -----------------------------------------------

    Optional<String> fetchAndParse(ScraperSource source) {
        try {
            log.debug("[Scraper] Fetch '{}': {}", source.name(), source.url());
            Document doc = Jsoup.connect(source.url())
                .userAgent(userAgent)
                .timeout(timeoutMs)
                .ignoreHttpErrors(true)
                .followRedirects(true)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
                .get();

            String text = extractRelevantText(doc);
            if (text.length() < minTextLength) {
                log.debug("[Scraper] Testo troppo corto da '{}': {} chars", source.name(), text.length());
                return Optional.empty();
            }
            if (!isTestoTecnico(text)) {
                log.warn("[Scraper] '{}' scartato: non è una scheda tecnica", source.name());
                return Optional.empty();
            }
            String enriched = "[FONTE: " + source.url() + "]\n" + sanitizeForAi(text);
            return Optional.of(truncate(enriched, maxTextLength));

        } catch (IOException e) {
            log.warn("[Scraper] Errore HTTP su '{}': {}", source.name(), e.getMessage());
            return Optional.empty();
        }
    }

    // -----------------------------------------------
    // VALIDATORE
    // -----------------------------------------------

    boolean isTestoTecnico(String testo) {
        if (testo == null || testo.isBlank()) return false;
        String lower = testo.toLowerCase();
        return KEYWORD_TECNICHE.stream().filter(lower::contains).count() >= 3;
    }

    // -----------------------------------------------
    // ESTRAZIONE TESTO HTML
    // -----------------------------------------------

    private String extractRelevantText(Document doc) {
        doc.select("nav, header, footer, script, style, iframe, noscript, " +
                   ".cookie-banner, .ads, .advertisement, #cookie, " +
                   ".social-share, .newsletter, .sidebar, .menu, " +
                   ".breadcrumb, .pagination").remove();

        String[] selectors = {
            ".scheda-tecnica", ".technical-specs", ".specs-table",
            ".specs-container", ".car-specs", ".dati-tecnici",
            "#specifiche-tecniche", "#dati-tecnici",
            ".scheda", ".tecnica", ".caratteristiche", ".specifiche",
            "table", "dl", "article", "main", ".content", "#content"
        };

        for (String selector : selectors) {
            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                String text = elements.text().trim();
                if (text.length() >= minTextLength) return text;
            }
        }

        Element body = doc.body();
        return body != null ? body.text().trim() : "";
    }

    // -----------------------------------------------
    // SANITIZZAZIONE PER AI
    // -----------------------------------------------

    private String sanitizeForAi(String text) {
        if (text == null) return "";
        return text
            .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "")
            .replace('"', '\'')
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
    }

    // -----------------------------------------------
    // UTILITY
    // -----------------------------------------------

    private String truncate(String text, int maxLen) {
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...[TRONCATO]";
    }

    public record ScraperSource(String name, String url) {}
}

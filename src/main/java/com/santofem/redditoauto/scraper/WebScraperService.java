package com.santofem.redditoauto.scraper;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Implementazione multi-fonte del WebScraper.
 *
 * STRATEGIA:
 * Interroga le fonti in ordine di priorita'. Alla prima risposta valida
 * (testo estratto con lunghezza minima), si ferma e restituisce il testo.
 * Se tutte le fonti falliscono, restituisce Optional.empty().
 *
 * FONTI (in ordine):
 * 1. Scheda Tecnica IT  — dati tecnici italiani, molto strutturati
 * 2. AutoScout24 IT     — prezzi e allestimenti
 * 3. Motorizzazione.it  — dati ufficiali omologati
 *
 * SICUREZZA:
 * - User-Agent configurabile (non usare il default Jsoup per evitare blocchi)
 * - Timeout configurabile
 * - Testo troncato a maxTextLength per non sprecare token Gemini
 * - Nessun cookie / sessione: solo GET stateless
 *
 * IMPORTANTE — Rispetto del robots.txt:
 * Questo servizio e' per uso personale/educativo.
 * In produzione valutare l'uso di API ufficiali (es. Car APIs a pagamento).
 */
@Service
@Slf4j
public class WebScraperService implements WebScraper {

    @Value("${scraper.timeout-ms:8000}")
    private int timeoutMs;

    @Value("${scraper.user-agent:Mozilla/5.0 (compatible; RedditoAutoBot/1.0)}")
    private String userAgent;

    @Value("${scraper.max-text-length:6000}")
    private int maxTextLength;

    @Value("${scraper.min-text-length:200}")
    private int minTextLength;

    // -----------------------------------------------
    // PUBLIC API
    // -----------------------------------------------

    @Override
    public Optional<String> scrape(String marca, String modello, String motore, int anno) {
        String query = buildQuery(marca, modello, motore, anno);
        log.info("[Scraper] Avvio ricerca per: {} {} {} {}", marca, modello, motore, anno);

        // Lista ordinata di fonti da provare
        List<ScraperSource> sources = List.of(
            new ScraperSource(
                "SchedaTecnica.it",
                buildSchedaTecnicaUrl(marca, modello, anno)
            ),
            new ScraperSource(
                "AutoScout24",
                buildAutoScout24Url(marca, modello, anno)
            ),
            new ScraperSource(
                "GoogleSearch",
                buildGoogleSearchUrl(query)
            )
        );

        for (ScraperSource source : sources) {
            Optional<String> result = fetchAndParse(source);
            if (result.isPresent()) {
                log.info("[Scraper] Testo estratto da '{}' ({} chars)",
                    source.name(), result.get().length());
                return result;
            }
        }

        log.warn("[Scraper] Nessuna fonte ha restituito dati per: {} {} {}",
            marca, modello, motore);
        return Optional.empty();
    }

    // -----------------------------------------------
    // FETCH + PARSE
    // -----------------------------------------------

    /**
     * Esegue il fetch HTTP e il parsing Jsoup per una singola fonte.
     * Non lancia eccezioni: restituisce Optional.empty() in caso di errore.
     */
    Optional<String> fetchAndParse(ScraperSource source) {
        try {
            log.debug("[Scraper] Tentativo su '{}': {}", source.name(), source.url());

            Document doc = Jsoup.connect(source.url())
                .userAgent(userAgent)
                .timeout(timeoutMs)
                .ignoreHttpErrors(true)      // non lancia su 4xx/5xx
                .followRedirects(true)
                .get();

            String text = extractRelevantText(doc, source.url());

            if (text.length() < minTextLength) {
                log.debug("[Scraper] Testo troppo corto da '{}': {} chars",
                    source.name(), text.length());
                return Optional.empty();
            }

            // Aggiungi metadata fonte per l'audit trail nel DB
            String enriched = "[FONTE: " + source.url() + "]\n" + text;
            return Optional.of(truncate(enriched, maxTextLength));

        } catch (IOException e) {
            log.warn("[Scraper] Errore HTTP su '{}': {}", source.name(), e.getMessage());
            return Optional.empty();
        }
    }

    // -----------------------------------------------
    // ESTRAZIONE TESTO HTML
    // -----------------------------------------------

    /**
     * Estrae il testo rilevante da un Document Jsoup.
     *
     * Strategia:
     * 1. Rimuove elementi non informativi (nav, header, footer, script, style, ads)
     * 2. Cerca selettori specifici per schede tecniche
     * 3. Fallback sul body completo
     *
     * L'output e' testo plain, piu' pulito possibile per ridurre
     * il "rumore" che potrebbe confondere Gemini.
     */
    private String extractRelevantText(Document doc, String sourceUrl) {
        // Step 1 — rimuovi noise
        doc.select("nav, header, footer, script, style, iframe, noscript, " +
                   ".cookie-banner, .ads, .advertisement, #cookie, " +
                   ".social-share, .newsletter, .related-articles").remove();

        // Step 2 — selettori ottimistici per schede tecniche
        String[] technicalSelectors = {
            ".scheda-tecnica",
            ".technical-specs",
            ".specs-table",
            ".car-specs",
            "table.specifiche",
            "[data-specs]",
            ".motorizzazione",
            "#specifiche-tecniche",
            ".dati-tecnici",
            "article",
            "main"
        };

        for (String selector : technicalSelectors) {
            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                String text = elements.text().trim();
                if (text.length() >= minTextLength) {
                    return text;
                }
            }
        }

        // Step 3 — fallback: intero body
        Element body = doc.body();
        return body != null ? body.text().trim() : "";
    }

    // -----------------------------------------------
    // URL BUILDERS
    // -----------------------------------------------

    private String buildSchedaTecnicaUrl(String marca, String modello, int anno) {
        // Formato: /marca/modello-anno/
        String m = slugify(marca);
        String mod = slugify(modello);
        return "https://www.schedatecnica.it/" + m + "/" + mod + "-" + anno + "/";
    }

    private String buildAutoScout24Url(String marca, String modello, int anno) {
        String q = encode(marca + " " + modello + " " + anno);
        return "https://www.autoscout24.it/lst?q=" + q;
    }

    private String buildGoogleSearchUrl(String query) {
        // Cerca direttamente scheda tecnica su Google
        String q = encode("scheda tecnica " + query + " consumi tagliando bollo");
        return "https://www.google.com/search?q=" + q;
    }

    private String buildQuery(String marca, String modello, String motore, int anno) {
        return marca + " " + modello + " " + motore + " " + anno;
    }

    // -----------------------------------------------
    // UTILITY
    // -----------------------------------------------

    private String slugify(String input) {
        return input.toLowerCase()
                    .trim()
                    .replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("^-|-$", "");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String truncate(String text, int maxLen) {
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...[TRONCATO]";
    }

    // -----------------------------------------------
    // INNER RECORD
    // -----------------------------------------------

    /**
     * Value object che rappresenta una singola fonte di scraping.
     */
    public record ScraperSource(String name, String url) {}
}

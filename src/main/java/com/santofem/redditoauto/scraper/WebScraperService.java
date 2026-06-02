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
import java.util.Set;

/**
 * WebScraperService - multi-fonte con HTML statico.
 *
 * automoto.it e' stato rimosso: il sito usa React/Vue e i dati versione
 * vengono caricati via JavaScript, Jsoup non riesce a vederli.
 *
 * FONTI (in ordine di priorita', tutte con HTML statico):
 * 1. auto.it          - schede tecniche italiane, HTML statico
 * 2. motorbox.com     - schede italiane, statico
 * 3. dati.guru        - aggregatore schede tecniche, statico
 * 4. car.info         - aggregatore EU multilingua, statico
 * 5. Bing search      - fallback: scarica la pagina risultati di ricerca
 *
 * FILTRO isTestoTecnico():
 * - Passa subito se contiene almeno 1 keyword numerica (l/100, cc, nm...)
 * - Altrimenti serve almeno 4 keyword generiche
 */
@Service
@Slf4j
public class WebScraperService implements WebScraper {

    @Value("${scraper.timeout-ms:8000}")
    private int timeoutMs;

    @Value("${scraper.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36}")
    private String userAgent;

    @Value("${scraper.max-text-length:6000}")
    private int maxTextLength;

    @Value("${scraper.min-text-length:200}")
    private int minTextLength;

    private static final Set<String> KEYWORD_GENERICHE = Set.of(
        "consumo", "kw", "cv", "cilindrata", "carburante", "diesel", "benzina",
        "potenza", "coppia", "cambio", "tagliando", "pneumatici", "emissioni",
        "autonomia", "wltp", "nedc", "cavalli", "motore", "cilindri"
    );

    private static final Set<String> KEYWORD_NUMERICHE = Set.of(
        "l/100", "l/100km", " cc", " nm", "rpm", "euro 6", "euro6",
        "g/km", "co2", "kwh", "mpi", "tdi", "tsi", "gdi", "crdi",
        "litri/100", "consumi urbani", "consumi extraurbani", "consumo misto",
        "cilindri in", "valvole per", "coppia massima"
    );

    // -----------------------------------------------
    // PUBLIC API
    // -----------------------------------------------

    @Override
    public Optional<String> scrape(String marca, String modello, String motore, int anno) {
        log.info("[Scraper] Avvio ricerca per: {} {} {} {}", marca, modello, motore, anno);

        List<ScraperSource> sources = buildSources(marca, modello, motore, anno);

        for (ScraperSource source : sources) {
            Optional<String> result = fetchAndParse(source);
            if (result.isPresent()) {
                log.info("[Scraper] Testo tecnico trovato da '{}' ({} chars)",
                    source.name(), result.get().length());
                return result;
            }
        }

        log.warn("[Scraper] Nessuna fonte ha restituito dati tecnici per: {} {} {}",
            marca, modello, motore);
        return Optional.empty();
    }

    // -----------------------------------------------
    // ELENCO FONTI
    // -----------------------------------------------

    private List<ScraperSource> buildSources(String marca, String modello, String motore, int anno) {
        return List.of(
            // auto.it: /schede_tecniche/marca/modello/anno/
            new ScraperSource("auto.it",
                "https://www.auto.it/schede_tecniche/"
                    + slugify(marca) + "/" + slugify(modello) + "/" + anno + "/"),

            // motorbox: /auto/scheda-tecnica/marca/modello-anno/
            new ScraperSource("motorbox.com",
                "https://www.motorbox.com/auto/scheda-tecnica/"
                    + slugify(marca) + "/" + slugify(modello) + "-" + anno + "/"),

            // dati.guru: /it/marca/modello/anno/
            new ScraperSource("dati.guru",
                "https://www.dati.guru/it/"
                    + slugify(marca) + "/" + slugify(modello) + "/" + anno + "/"),

            // car.info: /it/marca/modello-anno/
            new ScraperSource("car.info",
                "https://www.car.info/it-it/" + slugify(marca)
                    + "/" + slugify(modello) + "-" + anno),

            // auto-data.net: buon aggregatore EU con HTML statico
            new ScraperSource("auto-data.net",
                "https://www.auto-data.net/it/" + slugify(marca)
                    + "-" + slugify(modello) + "-" + anno + "-specs-1.html"),

            // Bing search fallback: scarica testo risultati di ricerca
            new ScraperSource("Bing",
                "https://www.bing.com/search?q="
                    + encode("scheda tecnica " + marca + " " + modello
                        + " " + motore + " " + anno
                        + " consumi kw cilindrata l/100km"))
        );
    }

    // -----------------------------------------------
    // FETCH + PARSE
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

            if (log.isDebugEnabled() && !text.isBlank()) {
                log.debug("[Scraper] Snippet '{}': [{}]",
                    source.name(), text.length() > 300 ? text.substring(0, 300) : text);
            }

            if (text.length() < minTextLength) {
                log.debug("[Scraper] Testo troppo corto da '{}': {} chars", source.name(), text.length());
                return Optional.empty();
            }

            if (!isTestoTecnico(text)) {
                log.warn("[Scraper] '{}' scartato: non e' una scheda tecnica ({} chars).",
                    source.name(), text.length());
                return Optional.empty();
            }

            String enriched = "[FONTE: " + source.url() + "]\n" + text;
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
        if (KEYWORD_NUMERICHE.stream().anyMatch(lower::contains)) return true;
        long count = KEYWORD_GENERICHE.stream().filter(lower::contains).count();
        return count >= 4;
    }

    // -----------------------------------------------
    // ESTRAZIONE TESTO HTML
    // -----------------------------------------------

    private String extractRelevantText(Document doc) {
        doc.select("nav, header, footer, script, style, iframe, noscript, " +
                   ".cookie-banner, .ads, .advertisement, #cookie, " +
                   ".social-share, .newsletter, .related-articles, " +
                   ".sidebar, .menu, .breadcrumb, .pagination").remove();

        // Selettori specifici per schede tecniche, dal piu' specifico al piu' generico
        String[] selectors = {
            // Selettori semantici specifici
            ".scheda-tecnica", ".technical-specs", ".specs-table",
            ".specs-container", ".car-specs", ".dati-tecnici",
            "#specifiche-tecniche", "#dati-tecnici", "[data-specs]",
            ".scheda", ".tecnica", ".caratteristiche", ".specifiche",
            // Elementi strutturali con dati tabulari
            "table", "dl",
            // Container generici
            "article", "main", ".content", "#content", ".container"
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
    // UTILITY
    // -----------------------------------------------

    private String slugify(String input) {
        return input.toLowerCase().trim()
                    .replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("^-|-$", "");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String truncate(String text, int maxLen) {
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...[TRONCATO]";
    }

    public record ScraperSource(String name, String url) {}
}

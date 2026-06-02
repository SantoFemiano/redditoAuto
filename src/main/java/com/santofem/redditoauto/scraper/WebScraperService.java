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
 * Implementazione multi-fonte del WebScraper.
 *
 * STRATEGIA:
 * Interroga le fonti in ordine di priorita'. Alla prima risposta che supera
 * la soglia minima di testo tecnico, si ferma.
 * Se tutte le fonti falliscono, restituisce Optional.empty().
 *
 * FONTI (in ordine di qualita' per schede tecniche):
 * 1. automoto.it     — schede tecniche italiane, HTML statico, ben strutturato
 * 2. auto.it         — dati tecnici italiani, HTML statico
 * 3. motorbox.com    — schede italiane, statico, buona copertura
 * 4. dati.guru       — aggregatore schede tecniche, statico
 * 5. bing.com search — fallback ricerca testuale (Bing < bot-protection di Google)
 *
 * SITI ESCLUSI:
 * - motorionline.com  : rendering JS, restituisce ~93 chars di HTML statico
 * - schedatecnica.it  : certificato SSL scaduto / SAN mismatch
 * - google.com/search : aggressivo anti-bot, restituisce ~86 chars
 * - autoscout24.it    : listing annunci, non schede tecniche
 * - mobile.de         : listing annunci in tedesco, ToS scraping vietato
 *
 * FILTRO isTestoTecnico():
 * Prima di restituire il testo, verifica che contenga almeno 2 keyword tecniche.
 * Difende dall'invio di listing/homepage/pagine di errore all'AI extractor.
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

    /** Keyword tecniche: almeno 2 devono essere presenti per considerare il testo valido. */
    private static final Set<String> KEYWORD_TECNICHE = Set.of(
        "consumo", "kw", "cv", "cilindrata", "carburante", "diesel", "benzina",
        "potenza", "coppia", "cambio", "tagliando", "pneumatici", "emissioni",
        "autonomia", "wltp", "nedc", "cavalli", "motore", "cilindri"
    );

    // -----------------------------------------------
    // PUBLIC API
    // -----------------------------------------------

    @Override
    public Optional<String> scrape(String marca, String modello, String motore, int anno) {
        log.info("[Scraper] Avvio ricerca per: {} {} {} {}", marca, modello, motore, anno);

        List<ScraperSource> sources = List.of(
            new ScraperSource(
                "automoto.it",
                buildAutoMotoUrl(marca, modello, anno)
            ),
            new ScraperSource(
                "auto.it",
                buildAutoItUrl(marca, modello, anno)
            ),
            new ScraperSource(
                "motorbox.com",
                buildMotorboxUrl(marca, modello, anno)
            ),
            new ScraperSource(
                "dati.guru",
                buildDatiGuruUrl(marca, modello, anno)
            ),
            new ScraperSource(
                "Bing",
                buildBingSearchUrl(marca, modello, motore, anno)
            )
        );

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
    // FETCH + PARSE
    // -----------------------------------------------

    Optional<String> fetchAndParse(ScraperSource source) {
        try {
            log.debug("[Scraper] Tentativo su '{}': {}", source.name(), source.url());

            Document doc = Jsoup.connect(source.url())
                .userAgent(userAgent)
                .timeout(timeoutMs)
                .ignoreHttpErrors(true)
                .followRedirects(true)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
                .get();

            String text = extractRelevantText(doc, source.url());

            if (text.length() < minTextLength) {
                log.debug("[Scraper] Testo troppo corto da '{}': {} chars",
                    source.name(), text.length());
                return Optional.empty();
            }

            if (!isTestoTecnico(text)) {
                log.warn("[Scraper] Testo da '{}' scartato: mancano keyword tecniche (probabile listing/homepage).",
                    source.name());
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
    // VALIDATORE QUALITA' TESTO
    // -----------------------------------------------

    boolean isTestoTecnico(String testo) {
        if (testo == null || testo.isBlank()) return false;
        String lower = testo.toLowerCase();
        long count = KEYWORD_TECNICHE.stream()
            .filter(lower::contains)
            .count();
        return count >= 2;
    }

    // -----------------------------------------------
    // ESTRAZIONE TESTO HTML
    // -----------------------------------------------

    private String extractRelevantText(Document doc, String sourceUrl) {
        // Rimuove elementi non utili
        doc.select("nav, header, footer, script, style, iframe, noscript, " +
                   ".cookie-banner, .ads, .advertisement, #cookie, " +
                   ".social-share, .newsletter, .related-articles, " +
                   ".sidebar, .menu, .breadcrumb, .pagination").remove();

        // Selettori specifici per schede tecniche, in ordine di specificita'
        String[] technicalSelectors = {
            // Selettori generici schede tecniche
            ".scheda-tecnica",
            ".technical-specs",
            ".specs-table",
            ".specs-container",
            ".car-specs",
            ".dati-tecnici",
            "#specifiche-tecniche",
            "#dati-tecnici",
            "[data-specs]",
            // Selettori per automoto.it
            ".scheda",
            ".tecnica",
            ".caratteristiche",
            // Tabelle con dati
            "table",
            // Contenuto principale
            "article",
            "main",
            ".content",
            "#content"
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

        Element body = doc.body();
        return body != null ? body.text().trim() : "";
    }

    // -----------------------------------------------
    // URL BUILDERS
    // -----------------------------------------------

    /**
     * automoto.it — formato: /schede-tecniche/marca/modello/anno/
     * Esempio: https://www.automoto.it/schede-tecniche/volkswagen/golf/2022/
     */
    private String buildAutoMotoUrl(String marca, String modello, int anno) {
        return "https://www.automoto.it/schede-tecniche/"
            + slugify(marca) + "/"
            + slugify(modello) + "/"
            + anno + "/";
    }

    /**
     * auto.it — formato: /schede-tecniche/marca-modello-anno.html
     * Esempio: https://www.auto.it/schede_tecniche/volkswagen/golf/2022/
     */
    private String buildAutoItUrl(String marca, String modello, int anno) {
        return "https://www.auto.it/schede_tecniche/"
            + slugify(marca) + "/"
            + slugify(modello) + "/"
            + anno + "/";
    }

    /**
     * motorbox.com — formato: /schede-tecniche/marca/modello-anno/
     * Esempio: https://www.motorbox.com/auto/scheda-tecnica/volkswagen/golf-2022/
     */
    private String buildMotorboxUrl(String marca, String modello, int anno) {
        return "https://www.motorbox.com/auto/scheda-tecnica/"
            + slugify(marca) + "/"
            + slugify(modello) + "-" + anno + "/";
    }

    /**
     * dati.guru — aggregatore internazionale di specifiche tecniche, HTML statico.
     * Esempio: https://www.dati.guru/it/volkswagen/golf/2022/
     */
    private String buildDatiGuruUrl(String marca, String modello, int anno) {
        return "https://www.dati.guru/it/"
            + slugify(marca) + "/"
            + slugify(modello) + "/"
            + anno + "/";
    }

    /**
     * Bing Search — meno aggressivo di Google anti-bot.
     * Cerca specificatamente schede tecniche italiane.
     */
    private String buildBingSearchUrl(String marca, String modello, String motore, int anno) {
        String q = encode("scheda tecnica " + marca + " " + modello + " "
            + motore + " " + anno + " consumi kw cilindrata tagliando site:it");
        return "https://www.bing.com/search?q=" + q;
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

    public record ScraperSource(String name, String url) {}
}

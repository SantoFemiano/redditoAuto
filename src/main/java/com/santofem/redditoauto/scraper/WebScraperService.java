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
 * FONTI (in ordine di qualita'):
 * 1. automoto.it  — due step: /listino/marca/modello -> segue link versione
 * 2. auto.it      — HTML statico
 * 3. motorbox.com — HTML statico
 * 4. dati.guru    — aggregatore, statico
 * 5. Bing         — fallback search
 *
 * FILTRO isTestoTecnico():
 * Keyword numeriche (l/100, cc, nm...) -> passa subito.
 * Altrimenti servono almeno 4 keyword generiche.
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
        "l/100", "l/100km", "cc", " nm", "rpm", "euro 6", "euro6",
        "kw/", "cv/", "g/km", "co2", "kwh", "mpi", "tdi", "tsi", "gdi"
    );

    // -----------------------------------------------
    // PUBLIC API
    // -----------------------------------------------

    @Override
    public Optional<String> scrape(String marca, String modello, String motore, int anno) {
        log.info("[Scraper] Avvio ricerca per: {} {} {} {}", marca, modello, motore, anno);

        // automoto.it: scraping in due step
        Optional<String> autoMoto = scrapeAutoMoto(marca, modello, motore, anno);
        if (autoMoto.isPresent()) return autoMoto;

        // Fonti dirette con URL statico
        List<ScraperSource> sources = List.of(
            new ScraperSource("auto.it",      buildAutoItUrl(marca, modello, anno)),
            new ScraperSource("motorbox.com", buildMotorboxUrl(marca, modello, anno)),
            new ScraperSource("dati.guru",    buildDatiGuruUrl(marca, modello, anno)),
            new ScraperSource("Bing",         buildBingSearchUrl(marca, modello, motore, anno))
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
    // AUTOMOTO.IT - SCRAPING IN DUE STEP
    // -----------------------------------------------

    /**
     * Step 1: carica /listino/marca/modello
     * Step 2: trova il link della versione piu' vicina a anno+motore
     * Step 3: carica la pagina della versione e restituisce il testo tecnico
     *
     * Struttura URL automoto.it:
     *   Listino:  https://www.automoto.it/listino/volkswagen/passat
     *   Versione: https://www.automoto.it/listino/volkswagen/passat?sc=sXXXXXXXXXXXXX
     */
    private Optional<String> scrapeAutoMoto(String marca, String modello, String motore, int anno) {
        String listUrl = "https://www.automoto.it/listino/"
            + slugify(marca) + "/" + slugify(modello);

        log.debug("[Scraper] automoto.it Step 1 - listino: {}", listUrl);

        try {
            Document listDoc = Jsoup.connect(listUrl)
                .userAgent(userAgent)
                .timeout(timeoutMs)
                .ignoreHttpErrors(true)
                .followRedirects(true)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
                .get();

            // Cerca tutti i link che contengono ?sc= (link versioni)
            String versUrl = findVersioneUrl(listDoc, listUrl, motore, anno);

            if (versUrl == null) {
                log.warn("[Scraper] automoto.it: nessun link versione trovato per {} {}", modello, anno);
                return Optional.empty();
            }

            log.debug("[Scraper] automoto.it Step 2 - versione: {}", versUrl);

            Optional<String> result = fetchAndParse(new ScraperSource("automoto.it", versUrl));
            if (result.isPresent()) {
                log.info("[Scraper] Testo tecnico trovato da 'automoto.it' ({} chars)",
                    result.get().length());
            }
            return result;

        } catch (IOException e) {
            log.warn("[Scraper] automoto.it Step 1 fallito: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Cerca nella pagina listino il link della versione piu' rilevante.
     * Priorita':
     * 1. Link che contiene l'anno E parte del nome motore (es. "tdi", "150")
     * 2. Link che contiene solo l'anno
     * 3. Primo link ?sc= trovato (fallback)
     */
    private String findVersioneUrl(Document doc, String baseUrl, String motore, int anno) {
        // I link versione su automoto.it contengono ?sc=
        Elements links = doc.select("a[href*='?sc=']");

        if (links.isEmpty()) {
            // Prova anche link che contengono /listino/ con parametri
            links = doc.select("a[href*='/listino/']");
        }

        log.debug("[Scraper] automoto.it: trovati {} link versione", links.size());

        String annoStr = String.valueOf(anno);
        String motoreLower = motore.toLowerCase();

        // Estrae parole chiave dal nome motore (es. "2.0 TDI 150CV" -> ["tdi", "150"])
        String[] motoreParts = motoreLower.split("[^a-z0-9]+");

        String bestMatch = null;
        String annoMatch = null;
        String fallback = null;

        for (Element link : links) {
            String href = link.absUrl("href");
            String text = link.text().toLowerCase();
            String combined = (href + " " + text).toLowerCase();

            if (fallback == null) fallback = href;

            boolean hasAnno = combined.contains(annoStr);

            // Cerca parti del nome motore nel testo del link
            boolean hasMotore = false;
            for (String part : motoreParts) {
                if (part.length() >= 2 && combined.contains(part)) {
                    hasMotore = true;
                    break;
                }
            }

            if (hasAnno && hasMotore && bestMatch == null) {
                bestMatch = href;
            } else if (hasAnno && annoMatch == null) {
                annoMatch = href;
            }
        }

        if (bestMatch != null) {
            log.debug("[Scraper] automoto.it: match anno+motore -> {}", bestMatch);
            return bestMatch;
        }
        if (annoMatch != null) {
            log.debug("[Scraper] automoto.it: match solo anno -> {}", annoMatch);
            return annoMatch;
        }
        if (fallback != null) {
            log.debug("[Scraper] automoto.it: fallback primo link -> {}", fallback);
            return fallback;
        }
        return null;
    }

    // -----------------------------------------------
    // FETCH + PARSE GENERICO
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

        String[] selectors = {
            ".scheda-tecnica", ".technical-specs", ".specs-table",
            ".specs-container", ".car-specs", ".dati-tecnici",
            "#specifiche-tecniche", "#dati-tecnici", "[data-specs]",
            ".scheda", ".tecnica", ".caratteristiche",
            ".listino-detail", ".car-detail", ".versione-detail",
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
    // URL BUILDERS
    // -----------------------------------------------

    private String buildAutoItUrl(String marca, String modello, int anno) {
        return "https://www.auto.it/schede_tecniche/"
            + slugify(marca) + "/" + slugify(modello) + "/" + anno + "/";
    }

    private String buildMotorboxUrl(String marca, String modello, int anno) {
        return "https://www.motorbox.com/auto/scheda-tecnica/"
            + slugify(marca) + "/" + slugify(modello) + "-" + anno + "/";
    }

    private String buildDatiGuruUrl(String marca, String modello, int anno) {
        return "https://www.dati.guru/it/"
            + slugify(marca) + "/" + slugify(modello) + "/" + anno + "/";
    }

    private String buildBingSearchUrl(String marca, String modello, String motore, int anno) {
        String q = encode("scheda tecnica " + marca + " " + modello + " "
            + motore + " " + anno + " consumi kw cilindrata site:it");
        return "https://www.bing.com/search?q=" + q;
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

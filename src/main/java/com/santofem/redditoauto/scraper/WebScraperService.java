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
 * la soglia minima DI TESTO TECNICO (non solo di lunghezza), si ferma.
 * Se tutte le fonti falliscono, restituisce Optional.empty().
 *
 * FONTI (in ordine di qualita' per schede tecniche):
 * 1. MotorOnline.it      — schede tecniche italiane strutturate
 * 2. SchedaTecnica.it    — dati tecnici italiani
 * 3. Motorizzazione.it   — dati ufficiali omologati
 * 4. GoogleSearch        — fallback generale
 *
 * NOTA: AutoScout24 e' stato RIMOSSO perche' restituisce listing di annunci,
 * non schede tecniche. Il testo estratto non contiene dati utili per l'AI
 * e causa isValid()=false → DataIntegrityViolationException sul DB.
 *
 * FILTRO isTestoTecnico():
 * Prima di restituire il testo, verifica che contenga keyword tecniche
 * (consumo, kw, cilindrata...). Testi che non le contengono vengono scartati
 * anche se superano la lunghezza minima.
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
        String query = buildQuery(marca, modello, motore, anno);
        log.info("[Scraper] Avvio ricerca per: {} {} {} {}", marca, modello, motore, anno);

        List<ScraperSource> sources = List.of(
            new ScraperSource(
                "MotorOnline",
                buildMotorOnlineUrl(marca, modello, anno)
            ),
            new ScraperSource(
                "SchedaTecnica.it",
                buildSchedaTecnicaUrl(marca, modello, anno)
            ),
            new ScraperSource(
                "GoogleSearch",
                buildGoogleSearchUrl(query)
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
                .get();

            String text = extractRelevantText(doc, source.url());

            if (text.length() < minTextLength) {
                log.debug("[Scraper] Testo troppo corto da '{}': {} chars",
                    source.name(), text.length());
                return Optional.empty();
            }

            // FILTRO QUALITA': scarta testi senza keyword tecniche
            // (es. listing annunci, pagine di categoria, risultati di ricerca generici)
            if (!isTestoTecnico(text)) {
                log.warn("[Scraper] Testo da '{}' scartato: mancano keyword tecniche. " +
                         "Probabile pagina di listing/annunci, non una scheda tecnica.",
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

    /**
     * Ritorna true se il testo contiene almeno 2 keyword tecniche.
     * Difende dall'invio all'AI di testi inutili (listing annunci,
     * pagine di ricerca, homepage) che causerebbero isValid()=false
     * e un tentativo di salvataggio con dati null nel DB.
     */
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
        doc.select("nav, header, footer, script, style, iframe, noscript, " +
                   ".cookie-banner, .ads, .advertisement, #cookie, " +
                   ".social-share, .newsletter, .related-articles").remove();

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

        Element body = doc.body();
        return body != null ? body.text().trim() : "";
    }

    // -----------------------------------------------
    // URL BUILDERS
    // -----------------------------------------------

    private String buildMotorOnlineUrl(String marca, String modello, int anno) {
        // Formato: /scheda-tecnica/marca/modello/anno/
        String m = slugify(marca);
        String mod = slugify(modello);
        return "https://www.motorionline.com/schede-tecniche/" + m + "/" + mod + "/";
    }

    private String buildSchedaTecnicaUrl(String marca, String modello, int anno) {
        String m = slugify(marca);
        String mod = slugify(modello);
        return "https://www.schedatecnica.it/" + m + "/" + mod + "-" + anno + "/";
    }

    private String buildGoogleSearchUrl(String query) {
        String q = encode("scheda tecnica " + query + " consumi tagliando kw cilindrata");
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

    public record ScraperSource(String name, String url) {}
}

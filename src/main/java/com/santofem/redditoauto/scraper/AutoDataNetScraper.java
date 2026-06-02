package com.santofem.redditoauto.scraper;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scraper per auto-data.net.
 *
 * STRATEGIA DI NAVIGAZIONE:
 *
 * La pagina /it/<marca>-brand-NNN carica i modelli via JavaScript:
 * Jsoup non esegue JS, quindi vede solo 0-2 modelli. NON si usa piu'.
 *
 * Si usa invece la search page:
 *   https://www.auto-data.net/it/results?search=<marca+modello>
 * che restituisce HTML statico con link -model- direttamente.
 *
 * Struttura URL dopo la ricerca:
 *   Livello 1 (search): /it/results?search=audi+tt
 *     -> link: /it/audi-tt-model-91            (contiene -model-)
 *
 *   Livello 2 (generazione): /it/audi-tt-model-91
 *     -> link: /it/audi-tt-8s-generation-5339  (contiene -generation-)
 *
 *   Livello 3 (motorizzazione): /it/audi-tt-8s-generation-5339
 *     -> link: /it/audi-tt-8s-2.0-tfsi-230hp-dsg-33928  (termina con numero)
 *
 *   Livello 4 (scheda tecnica): tabella HTML
 *
 * MATCHING MOTORIZZAZIONE:
 *   +3  match potenza numerica (CV == HP numericamente)
 *   +2  match tipo carburante (tdi, tsi, tfsi...)
 *   +1  match displacement (2.0, 1.6...)
 *   +1  match cambio (dsg, cvt...)
 */
@Component
@Slf4j
public class AutoDataNetScraper {

    private static final String BASE_IT    = "https://www.auto-data.net/it";
    private static final String SEARCH_URL = BASE_IT + "/results?search=";

    private static final Pattern ENDS_WITH_NUMBER    = Pattern.compile("-\\d+$");
    private static final Pattern YEAR_PATTERN        = Pattern.compile("(20|19)\\d{2}");
    private static final Pattern POWER_PATTERN       = Pattern.compile("(\\d{2,4})\\s*(?:cv|hp|kw)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DISPLACEMENT_PATTERN = Pattern.compile("(\\d\\.\\d)");

    private static final List<String> FUEL_TOKENS = List.of(
        "tdi", "tsi", "tfsi", "gdi", "crdi", "cdti", "jtd", "hdi",
        "gte", "phev", "hybrid", "ibrido", "elettrico", "electric",
        "mpi", "fsi", "gpl", "cng", "etg", "tce", "puretech"
    );

    private static final List<String> GEARBOX_TOKENS = List.of(
        "dsg", "dct", "cvt", "automatic", "automatico", "manual", "manuale",
        "pdk", "edct", "xdrive", "quattro"
    );

    @Value("${scraper.timeout-ms:10000}")
    private int timeoutMs;

    @Value("${scraper.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36}")
    private String userAgent;

    // ═══════════════════════════════════════════════
    //  ENTRY POINT
    // ═══════════════════════════════════════════════

    public Optional<String> scrape(String marca, String modello, String motore, int anno) {
        log.info("[AutoDataNet] Navigazione: {} {} {} {}", marca, modello, motore, anno);
        try {
            // Livello 1: search -> model URL
            String modelUrl = findModelUrlViaSearch(marca, modello);
            if (modelUrl == null) {
                log.warn("[AutoDataNet] Modello non trovato via search: {} {}", marca, modello);
                return Optional.empty();
            }
            log.debug("[AutoDataNet] Livello 2 - Modello: {}", modelUrl);

            // Livello 2: model -> generazione
            String genUrl = findGenerazioneUrl(modelUrl, anno);
            if (genUrl == null) {
                log.warn("[AutoDataNet] Generazione non trovata per anno {} su {}", anno, modelUrl);
                return Optional.empty();
            }
            log.debug("[AutoDataNet] Livello 3 - Generazione: {}", genUrl);

            // Livello 3: generazione -> motorizzazione
            String motorUrl = findMotorizzazioneUrl(genUrl, motore);
            if (motorUrl == null) {
                log.warn("[AutoDataNet] Motorizzazione non trovata: {} su {}", motore, genUrl);
                return Optional.empty();
            }
            log.debug("[AutoDataNet] Livello 4 - Motorizzazione: {}", motorUrl);

            return fetchSchedaTecnica(motorUrl);

        } catch (Exception e) {
            log.warn("[AutoDataNet] Errore durante la navigazione: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ═══════════════════════════════════════════════
    //  LIVELLO 1 — SEARCH → MODEL URL
    // ═══════════════════════════════════════════════

    private String findModelUrlViaSearch(String marca, String modello) throws IOException {
        // Prima prova con marca+modello insieme
        String query   = marca.trim() + " " + modello.trim();
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String searchUrl = SEARCH_URL + encoded;
        log.debug("[AutoDataNet] Search URL: {}", searchUrl);

        Document doc = fetch(searchUrl);
        List<LinkEntry> candidates = extractByPattern(doc, "-model-");

        // Se non trova nulla, prova con solo il modello
        if (candidates.isEmpty()) {
            log.debug("[AutoDataNet] Search '{} {}': 0 risultati, riprovo con solo modello", marca, modello);
            encoded   = URLEncoder.encode(modello.trim(), StandardCharsets.UTF_8);
            searchUrl = SEARCH_URL + encoded;
            doc       = fetch(searchUrl);
            candidates = extractByPattern(doc, "-model-");
        }

        log.debug("[AutoDataNet] Modelli trovati via search: {} | es: {}",
            candidates.size(),
            candidates.isEmpty() ? "nessuno" : candidates.get(0).name() + " -> " + candidates.get(0).url());

        if (candidates.isEmpty()) return null;

        // Filtra per marca nell'URL e cerca best match per modello
        String marcaLow = normalize(marca);
        List<LinkEntry> filtered = candidates.stream()
            .filter(c -> c.url().toLowerCase().contains(marcaLow))
            .toList();

        List<LinkEntry> pool = filtered.isEmpty() ? candidates : filtered;
        return bestMatch(normalize(modello), pool);
    }

    // ═══════════════════════════════════════════════
    //  LIVELLO 2 — GENERAZIONE
    // ═══════════════════════════════════════════════

    private String findGenerazioneUrl(String modelUrl, int anno) throws IOException {
        Document doc = fetch(modelUrl);

        String bestUrl   = null;
        int    bestScore = Integer.MIN_VALUE;
        String fallback  = null;

        for (Element a : doc.select("a[href*=-generation-]")) {
            String href = absoluteHref(a);
            if (href.isEmpty()) continue;
            if (fallback == null) fallback = href;

            Element container = a.closest("div");
            String ctx = container != null ? container.text() : a.text();

            int[] range = extractYearRange(ctx);
            if (range == null) continue;

            int from = range[0], to = range[1];
            if (anno >= from && anno <= to) {
                if (from > bestScore) { bestScore = from; bestUrl = href; }
            }
        }

        if (bestUrl == null && fallback != null) {
            log.debug("[AutoDataNet] Generazione: nessun match per anno {}, uso prima: {}", anno, fallback);
            return fallback;
        }
        return bestUrl;
    }

    private int[] extractYearRange(String text) {
        List<Integer> years = new ArrayList<>();
        Matcher m = YEAR_PATTERN.matcher(text);
        while (m.find()) years.add(Integer.parseInt(m.group()));
        if (years.isEmpty()) return null;
        Collections.sort(years);
        return new int[]{ years.get(0), years.size() > 1 ? years.get(years.size() - 1) : 9999 };
    }

    // ═══════════════════════════════════════════════
    //  LIVELLO 3 — MOTORIZZAZIONE (scoring pesato)
    // ═══════════════════════════════════════════════

    private String findMotorizzazioneUrl(String genUrl, String motore) throws IOException {
        Document doc = fetch(genUrl);
        String ml = normalize(motore);

        int    requestedPower        = extractPower(ml);
        String requestedDisplacement = extractDisplacement(ml);
        List<String> requestedFuel   = matchingTokens(ml, FUEL_TOKENS);
        List<String> requestedGearbox= matchingTokens(ml, GEARBOX_TOKENS);

        log.debug("[AutoDataNet] Matching motore '{}': potenza={} displacement={} fuel={} gearbox={}",
            ml, requestedPower, requestedDisplacement, requestedFuel, requestedGearbox);

        List<LinkEntry> candidates = new ArrayList<>();
        for (Element a : doc.select("a[href]")) {
            String href = absoluteHref(a);
            if (href.isEmpty()) continue;
            if (!href.startsWith(BASE_IT + "/")) continue;
            if (href.contains("-generation-") || href.contains("-model-") ||
                href.contains("-brand-")       || href.endsWith("/it/") ||
                href.contains("/login")         || href.contains("/search") ||
                href.contains("/register")      || href.contains("/allbrands") ||
                href.contains("/results")) continue;
            if (!ENDS_WITH_NUMBER.matcher(href).find()) continue;
            if (href.equals(genUrl)) continue;

            Element tit  = a.selectFirst(".tit");
            String  name = normalize(tit != null ? tit.text() : a.text());
            if (!name.isEmpty()) candidates.add(new LinkEntry(name, href));
        }

        // Dedup per href
        Map<String, LinkEntry> dedup = new LinkedHashMap<>();
        for (LinkEntry e : candidates) dedup.put(e.url(), e);
        candidates = new ArrayList<>(dedup.values());

        log.debug("[AutoDataNet] Motorizzazioni trovate: {} | es: {}",
            candidates.size(),
            candidates.isEmpty() ? "nessuna" : candidates.get(0).name() + " -> " + candidates.get(0).url());

        if (candidates.isEmpty()) return null;

        String bestUrl   = null;
        int    bestScore = -1;

        for (LinkEntry c : candidates) {
            String urlLower = c.url().toLowerCase();
            int score = 0;

            if (requestedPower > 0 && (urlLower.contains(requestedPower + "hp") ||
                    c.name().contains(String.valueOf(requestedPower)))) score += 3;

            for (String ft : requestedFuel)
                if (urlLower.contains(ft) || c.name().contains(ft)) { score += 2; break; }

            if (requestedDisplacement != null &&
                    (urlLower.contains(requestedDisplacement) || c.name().contains(requestedDisplacement))) score += 1;

            for (String gt : requestedGearbox)
                if (urlLower.contains(gt) || c.name().contains(gt)) { score += 1; break; }

            log.debug("[AutoDataNet] Candidato score={}: {} -> {}", score, c.name(), c.url());
            if (score > bestScore) { bestScore = score; bestUrl = c.url(); }
        }

        log.debug("[AutoDataNet] Miglior match (score={}): {}", bestScore, bestUrl);
        return bestUrl != null ? bestUrl : candidates.get(0).url();
    }

    // ═══════════════════════════════════════════════
    //  LIVELLO 4 — SCHEDA TECNICA
    // ═══════════════════════════════════════════════

    private Optional<String> fetchSchedaTecnica(String url) throws IOException {
        log.info("[AutoDataNet] Scarico scheda tecnica: {}", url);
        Document doc = fetch(url);
        doc.select("nav, header, footer, script, style, iframe, .ad970, .ad970_250, .ads").remove();

        StringBuilder sb = new StringBuilder();
        sb.append("[FONTE: ").append(url).append("]\n");
        sb.append(doc.title()).append("\n\n");

        for (Element table : doc.select("table")) {
            for (Element row : table.select("tr")) {
                Elements cells = row.select("td, th");
                if (cells.size() >= 2) {
                    String label = cells.get(0).text().trim();
                    String value = cells.get(1).text().trim();
                    if (!label.isEmpty() && !value.isEmpty())
                        sb.append(label).append(": ").append(value).append("\n");
                }
            }
            sb.append("\n");
        }

        if (sb.length() < 400) {
            Element body = doc.body();
            if (body != null) sb.append(body.text());
        }

        log.info("[AutoDataNet] Scheda estratta: {} chars da {}", sb.length(), url);
        return sb.length() > 200 ? Optional.of(sb.toString()) : Optional.empty();
    }

    // ═══════════════════════════════════════════════
    //  UTILITY
    // ═══════════════════════════════════════════════

    private List<LinkEntry> extractByPattern(Document doc, String urlPattern) {
        List<LinkEntry> candidates = new ArrayList<>();
        for (Element a : doc.select("a[href*=" + urlPattern + "]")) {
            String href = absoluteHref(a);
            if (href.isEmpty()) continue;
            Element strong = a.selectFirst("strong");
            String name = normalize(strong != null ? strong.text() : a.text());
            if (!name.isEmpty()) candidates.add(new LinkEntry(name, href));
        }
        Map<String, LinkEntry> dedup = new LinkedHashMap<>();
        for (LinkEntry e : candidates) dedup.put(e.url(), e);
        return new ArrayList<>(dedup.values());
    }

    private int extractPower(String motore) {
        Matcher m = POWER_PATTERN.matcher(motore);
        while (m.find()) {
            try { int v = Integer.parseInt(m.group(1)); if (v >= 40 && v <= 700) return v; }
            catch (NumberFormatException ignored) {}
        }
        Matcher fb = Pattern.compile("(?<![.\\d])(\\d{2,3})(?![.\\d])").matcher(motore);
        while (fb.find()) {
            try { int v = Integer.parseInt(fb.group(1)); if (v >= 40 && v <= 700) return v; }
            catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private String extractDisplacement(String motore) {
        Matcher m = DISPLACEMENT_PATTERN.matcher(motore);
        return m.find() ? m.group(1) : null;
    }

    private List<String> matchingTokens(String motore, List<String> tokens) {
        List<String> found = new ArrayList<>();
        for (String t : tokens) if (motore.contains(t)) found.add(t);
        return found;
    }

    private String bestMatch(String target, List<LinkEntry> candidates) {
        if (candidates.isEmpty()) return null;
        for (LinkEntry c : candidates) if (c.name().equals(target)) return c.url();
        for (LinkEntry c : candidates) if (c.name().contains(target) || target.contains(c.name())) return c.url();
        String[] words = target.split("\\s+");
        String best = null; int bestScore = 0;
        for (LinkEntry c : candidates) {
            int score = 0;
            for (String w : words) if (w.length() > 1 && c.name().contains(w)) score++;
            if (score > bestScore) { bestScore = score; best = c.url(); }
        }
        return best != null ? best : candidates.get(0).url();
    }

    private String absoluteHref(Element a) {
        String href = a.attr("href");
        if (href == null || href.isBlank()) return "";
        if (href.startsWith("http")) return href;
        if (href.startsWith("/")) return "https://www.auto-data.net" + href;
        return "";
    }

    private String normalize(String s) {
        return s == null ? "" : s.toLowerCase().trim();
    }

    private Document fetch(String url) throws IOException {
        return Jsoup.connect(url)
            .userAgent(userAgent)
            .timeout(timeoutMs)
            .ignoreHttpErrors(true)
            .followRedirects(true)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.8,*/*;q=0.8")
            .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
            .header("Accept-Encoding", "gzip, deflate")
            .referrer("https://www.auto-data.net/it/")
            .get();
    }

    private record LinkEntry(String name, String url) {}
}

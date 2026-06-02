package com.santofem.redditoauto.scraper;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Scraper dedicato a auto-data.net con navigazione a cascata 4 livelli.
 *
 * STRUTTURA URL reale auto-data.net:
 *   /it/allbrands
 *      link formato: /it/volkswagen-brand-80
 *   /it/volkswagen-brand-80
 *      link formato: /it/volkswagen-passat-models-227
 *   /it/volkswagen-passat-models-227
 *      link formato: /it/volkswagen-passat-b8-facelift-2019-47526  (no '-models-', no '-brand-')
 *   /it/volkswagen-passat-b8-facelift-2019-47526
 *      link formato: /it/volkswagen-passat-b8-2.0-tdi-150hp-...-48000
 *   /it/volkswagen-passat-b8-2.0-tdi-150hp-...-48000
 *      -> scheda tecnica con tabelle kv
 */
@Component
@Slf4j
public class AutoDataNetScraper {

    private static final String BASE   = "https://www.auto-data.net";
    private static final String BASE_IT = BASE + "/it";
    private static final Pattern ID_SUFFIX = Pattern.compile("-\\d+$");

    @Value("${scraper.timeout-ms:8000}")
    private int timeoutMs;

    @Value("${scraper.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36}")
    private String userAgent;

    private final Map<String, String> brandCache = new ConcurrentHashMap<>();
    private volatile Instant brandCacheTime = Instant.EPOCH;
    private static final long CACHE_TTL_SECONDS = 86400;

    // -----------------------------------------------
    // ENTRY POINT
    // -----------------------------------------------

    public Optional<String> scrape(String marca, String modello, String motore, int anno) {
        log.info("[AutoDataNet] Navigazione: {} {} {} {}", marca, modello, motore, anno);
        try {
            String brandUrl = findBrandUrl(marca);
            if (brandUrl == null) { log.warn("[AutoDataNet] Marca non trovata: {}", marca); return Optional.empty(); }
            log.debug("[AutoDataNet] Marca: {}", brandUrl);

            String modelUrl = findModelUrl(brandUrl, modello);
            if (modelUrl == null) { log.warn("[AutoDataNet] Modello non trovato: {}", modello); return Optional.empty(); }
            log.debug("[AutoDataNet] Modello: {}", modelUrl);

            String genUrl = findGenerazioneUrl(modelUrl, anno);
            if (genUrl == null) { log.warn("[AutoDataNet] Generazione non trovata per anno {}", anno); return Optional.empty(); }
            log.debug("[AutoDataNet] Generazione: {}", genUrl);

            String motorUrl = findMotorizzazioneUrl(genUrl, motore);
            if (motorUrl == null) { log.warn("[AutoDataNet] Motorizzazione non trovata: {}", motore); return Optional.empty(); }
            log.debug("[AutoDataNet] Motorizzazione: {}", motorUrl);

            return fetchSchedaTecnica(motorUrl);

        } catch (Exception e) {
            log.warn("[AutoDataNet] Errore: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // -----------------------------------------------
    // LIVELLO 1 - MARCA
    // -----------------------------------------------

    private String findBrandUrl(String marca) throws IOException {
        refreshBrandCacheIfNeeded();
        String ml = marca.toLowerCase().trim();
        if (brandCache.containsKey(ml)) return brandCache.get(ml);
        return brandCache.entrySet().stream()
            .filter(e -> e.getKey().contains(ml) || ml.contains(e.getKey()))
            .map(Map.Entry::getValue)
            .findFirst().orElse(null);
    }

    private void refreshBrandCacheIfNeeded() throws IOException {
        long elapsed = Instant.now().getEpochSecond() - brandCacheTime.getEpochSecond();
        if (elapsed < CACHE_TTL_SECONDS && !brandCache.isEmpty()) return;

        log.debug("[AutoDataNet] Refresh cache marche");
        Document doc = get(BASE_IT + "/allbrands");

        for (Element a : doc.select("a[href]")) {
            String href = a.absUrl("href");
            if (!href.contains("-brand-")) continue;
            String name = a.text().toLowerCase().trim();
            if (!name.isBlank()) brandCache.put(name, href);
        }
        brandCacheTime = Instant.now();
        log.debug("[AutoDataNet] Cache marche: {} voci", brandCache.size());
    }

    // -----------------------------------------------
    // LIVELLO 2 - MODELLO
    // -----------------------------------------------

    private String findModelUrl(String brandUrl, String modello) throws IOException {
        Document doc = get(brandUrl);
        String ml = modello.toLowerCase().trim();

        List<LinkEntry> candidates = new ArrayList<>();
        for (Element a : doc.select("a[href]")) {
            String href = a.absUrl("href");
            // I link modello contengono -models- nell'href
            if (!href.contains("-models-")) continue;
            String name = a.text().toLowerCase().trim();
            if (!name.isBlank()) {
                candidates.add(new LinkEntry(name, href));
            } else {
                // Fallback: estrai nome dal path URL
                String path = href.replaceAll(".*/it/", "").replaceAll("-models-.*", "");
                // rimuove il prefisso marca (es. "volkswagen-")
                path = path.replaceFirst("^[a-z]+-", "");
                candidates.add(new LinkEntry(path.replace("-", " "), href));
            }
        }

        if (candidates.isEmpty()) {
            // Se Jsoup non ha trovato testo nei link, prova a leggere il raw HTML
            log.debug("[AutoDataNet] Nessun link -models- trovato con testo, provo raw href");
            for (Element a : doc.select("a[href*=-models-]")) {
                String href = a.absUrl("href");
                String path = href.replaceAll(".*/it/", "")
                                  .replaceAll("-models-.*", "")
                                  .replaceFirst("^[a-z]+-", "")
                                  .replace("-", " ");
                candidates.add(new LinkEntry(path, href));
            }
        }

        log.debug("[AutoDataNet] Modelli trovati: {} (es: {})",
            candidates.size(),
            candidates.isEmpty() ? "nessuno" : candidates.get(0).name() + " -> " + candidates.get(0).url());

        return bestMatch(ml, candidates);
    }

    // -----------------------------------------------
    // LIVELLO 3 - GENERAZIONE
    // -----------------------------------------------

    private String findGenerazioneUrl(String modelUrl, int anno) throws IOException {
        Document doc = get(modelUrl);

        String best = null;
        int bestDist = Integer.MAX_VALUE;
        String fallback = null;

        for (Element a : doc.select("a[href]")) {
            String href = a.absUrl("href");
            // Link generazione: contiene /it/, non e' brand ne' models, ha ID numerico in fondo
            if (!href.startsWith(BASE_IT + "/")) continue;
            if (href.contains("-brand-") || href.contains("-models-")) continue;
            if (!ID_SUFFIX.matcher(href).find()) continue;

            if (fallback == null) fallback = href;

            // Cerca anni nel testo del link o nell'elemento circostante
            Element ctx = a.closest("tr,li,div,td");
            String text = (a.text() + " " + (ctx != null ? ctx.text() : "")).toLowerCase();

            int[] range = extractYearRange(text);
            if (range == null) continue;

            int from = range[0], to = range[1];
            if (anno >= from && anno <= to) {
                int dist = anno - from;
                if (dist < bestDist) { bestDist = dist; best = href; }
            }
        }

        if (best == null && fallback != null) {
            log.debug("[AutoDataNet] Generazione: nessun match anno, uso fallback {}", fallback);
            return fallback;
        }
        return best;
    }

    private int[] extractYearRange(String text) {
        List<Integer> years = new ArrayList<>();
        java.util.regex.Matcher m = Pattern.compile("(19|20)\\d{2}").matcher(text);
        while (m.find()) years.add(Integer.parseInt(m.group()));
        if (years.isEmpty()) return null;
        int from = years.get(0);
        int to   = years.size() > 1 ? years.get(years.size() - 1) : 9999;
        return new int[]{from, to};
    }

    // -----------------------------------------------
    // LIVELLO 4 - MOTORIZZAZIONE
    // -----------------------------------------------

    private String findMotorizzazioneUrl(String genUrl, String motore) throws IOException {
        Document doc = get(genUrl);
        String ml = motore.toLowerCase();
        String[] parts = ml.split("[^a-z0-9.]+");

        List<LinkEntry> candidates = new ArrayList<>();
        for (Element a : doc.select("a[href]")) {
            String href = a.absUrl("href");
            if (!href.startsWith(BASE_IT + "/")) continue;
            if (href.contains("-brand-") || href.contains("-models-")) continue;
            if (href.equals(genUrl)) continue;
            if (!ID_SUFFIX.matcher(href).find()) continue;
            String name = a.text().toLowerCase().trim();
            if (name.isBlank()) {
                name = href.replaceAll(".*/it/", "").replaceAll("-\\d+$", "").replace("-", " ");
            }
            candidates.add(new LinkEntry(name, href));
        }

        log.debug("[AutoDataNet] Motorizzazioni trovate: {} (es: {})",
            candidates.size(),
            candidates.isEmpty() ? "nessuna" : candidates.get(0).name() + " -> " + candidates.get(0).url());

        if (candidates.isEmpty()) return null;

        String bestUrl = null;
        int bestScore = -1;
        for (LinkEntry c : candidates) {
            int score = 0;
            for (String part : parts) {
                if (part.length() >= 2 && c.name().contains(part)) score++;
            }
            if (score > bestScore) { bestScore = score; bestUrl = c.url(); }
        }
        return bestUrl != null ? bestUrl : candidates.get(0).url();
    }

    // -----------------------------------------------
    // SCHEDA TECNICA
    // -----------------------------------------------

    private Optional<String> fetchSchedaTecnica(String url) throws IOException {
        log.info("[AutoDataNet] Scarico scheda: {}", url);
        Document doc = get(url);
        doc.select("nav,header,footer,script,style,iframe,.ads,.cookie").remove();

        StringBuilder sb = new StringBuilder();
        sb.append("[FONTE: ").append(url).append("]\n");
        sb.append(doc.title()).append("\n\n");

        for (Element table : doc.select("table")) {
            for (Element row : table.select("tr")) {
                var cells = row.select("td,th");
                if (cells.size() >= 2) {
                    sb.append(cells.get(0).text().trim())
                      .append(": ")
                      .append(cells.get(1).text().trim())
                      .append("\n");
                }
            }
            sb.append("\n");
        }

        if (sb.length() < 300) {
            Element body = doc.body();
            if (body != null) sb.append(body.text());
        }

        log.debug("[AutoDataNet] Scheda: {} chars", sb.length());
        return sb.length() > 100 ? Optional.of(sb.toString()) : Optional.empty();
    }

    // -----------------------------------------------
    // UTILITY
    // -----------------------------------------------

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

    private Document get(String url) throws IOException {
        return Jsoup.connect(url)
            .userAgent(userAgent)
            .timeout(timeoutMs)
            .ignoreHttpErrors(true)
            .followRedirects(true)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
            .get();
    }

    private record LinkEntry(String name, String url) {}
}

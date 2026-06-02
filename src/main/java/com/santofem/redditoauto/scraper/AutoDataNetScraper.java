package com.santofem.redditoauto.scraper;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scraper dedicato a auto-data.net con navigazione a cascata 4 livelli.
 *
 * STRUTTURA URL auto-data.net:
 *   /it/allbrands
 *      -> /it/{marca}-brand-{ID}             (lista modelli)
 *         -> /it/{marca}-{modello}-models-{ID}   (lista generazioni)
 *            -> /it/{slug-generazione}-{ID}       (lista motorizzazioni)
 *               -> /it/{slug-motorizzazione}-{ID} (scheda tecnica)
 *
 * SELEZIONE:
 *   Marca:         best match per nome (case-insensitive, similarity)
 *   Modello:       best match per nome
 *   Generazione:   range anni che include l'anno richiesto
 *   Motorizzazione: match su tipo carburante + potenza piu' vicina
 */
@Component
@Slf4j
public class AutoDataNetScraper {

    private static final String BASE = "https://www.auto-data.net/it";

    @Value("${scraper.timeout-ms:8000}")
    private int timeoutMs;

    @Value("${scraper.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36}")
    private String userAgent;

    // Cache brand list: slug -> url, si invalida ogni 24h
    private final Map<String, String> brandCache = new ConcurrentHashMap<>();
    private volatile Instant brandCacheTime = Instant.EPOCH;
    private static final long CACHE_TTL_SECONDS = 86400;

    // -----------------------------------------------
    // ENTRY POINT
    // -----------------------------------------------

    /**
     * Naviga auto-data.net in 4 livelli e restituisce il testo della scheda tecnica.
     *
     * @param marca   es. "Volkswagen"
     * @param modello es. "Passat"
     * @param motore  es. "2.0 TDI 150CV" (usato per matching motorizzazione)
     * @param anno    es. 2022
     */
    public Optional<String> scrape(String marca, String modello, String motore, int anno) {
        log.info("[AutoDataNet] Avvio navigazione: {} {} {} {}", marca, modello, motore, anno);
        try {
            // LIVELLO 1: trova URL marca
            String brandUrl = findBrandUrl(marca);
            if (brandUrl == null) {
                log.warn("[AutoDataNet] Marca non trovata: {}", marca);
                return Optional.empty();
            }
            log.debug("[AutoDataNet] Marca URL: {}", brandUrl);

            // LIVELLO 2: trova URL modello
            String modelUrl = findModelUrl(brandUrl, modello);
            if (modelUrl == null) {
                log.warn("[AutoDataNet] Modello non trovato: {} per marca {}", modello, marca);
                return Optional.empty();
            }
            log.debug("[AutoDataNet] Modello URL: {}", modelUrl);

            // LIVELLO 3: trova URL generazione per anno
            String genUrl = findGenerazioneUrl(modelUrl, anno);
            if (genUrl == null) {
                log.warn("[AutoDataNet] Generazione non trovata per anno {}", anno);
                return Optional.empty();
            }
            log.debug("[AutoDataNet] Generazione URL: {}", genUrl);

            // LIVELLO 4: trova URL motorizzazione
            String motorUrl = findMotorizzazioneUrl(genUrl, motore, anno);
            if (motorUrl == null) {
                log.warn("[AutoDataNet] Motorizzazione non trovata per: {}", motore);
                return Optional.empty();
            }
            log.debug("[AutoDataNet] Motorizzazione URL: {}", motorUrl);

            // SCHEDA TECNICA: scarica e restituisce il testo
            return fetchSchedaTecnica(motorUrl);

        } catch (Exception e) {
            log.warn("[AutoDataNet] Errore navigazione: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // -----------------------------------------------
    // LIVELLO 1: MARCA
    // -----------------------------------------------

    private String findBrandUrl(String marca) throws IOException {
        refreshBrandCacheIfNeeded();
        String marcaLower = marca.toLowerCase().trim();

        // Match esatto
        if (brandCache.containsKey(marcaLower)) {
            return brandCache.get(marcaLower);
        }

        // Match parziale (es. "mercedes-benz" contiene "mercedes")
        return brandCache.entrySet().stream()
            .filter(e -> e.getKey().contains(marcaLower) || marcaLower.contains(e.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }

    private void refreshBrandCacheIfNeeded() throws IOException {
        long secondsSince = Instant.now().getEpochSecond() - brandCacheTime.getEpochSecond();
        if (secondsSince < CACHE_TTL_SECONDS && !brandCache.isEmpty()) return;

        log.debug("[AutoDataNet] Aggiorno cache marche da /allbrands");
        Document doc = get(BASE + "/allbrands");

        // Link formato: /it/volkswagen-brand-14
        for (Element a : doc.select("a[href*='-brand-']")) {
            String href = a.absUrl("href");
            String name = a.text().toLowerCase().trim();
            if (!name.isBlank() && href.contains("-brand-")) {
                brandCache.put(name, href);
            }
        }
        brandCacheTime = Instant.now();
        log.debug("[AutoDataNet] Cache marche: {} voci", brandCache.size());
    }

    // -----------------------------------------------
    // LIVELLO 2: MODELLO
    // -----------------------------------------------

    private String findModelUrl(String brandUrl, String modello) throws IOException {
        Document doc = get(brandUrl);
        String modelloLower = modello.toLowerCase().trim();

        // Link formato: /it/volkswagen-passat-models-227
        List<LinkEntry> candidates = new ArrayList<>();
        for (Element a : doc.select("a[href*='-models-']")) {
            String href = a.absUrl("href");
            String name = a.text().toLowerCase().trim();
            if (!name.isBlank()) {
                candidates.add(new LinkEntry(name, href));
            }
        }

        log.debug("[AutoDataNet] Modelli trovati per marca: {}", candidates.size());
        return bestMatch(modelloLower, candidates);
    }

    // -----------------------------------------------
    // LIVELLO 3: GENERAZIONE
    // -----------------------------------------------

    private String findGenerazioneUrl(String modelUrl, int anno) throws IOException {
        Document doc = get(modelUrl);

        // Link generazione formato: /it/volkswagen-passat-b8-facelift-2019-47526
        // Il testo del link contiene es. "2019 - 2023" o "2015 -"
        String best = null;
        int bestDist = Integer.MAX_VALUE;

        for (Element a : doc.select("a[href]")) {
            String href = a.absUrl("href");
            // Filtra: deve essere un link generazione (contiene anno nel testo o href)
            if (!href.contains("auto-data.net/it/")) continue;
            if (href.contains("-brand-") || href.contains("-models-")) continue;

            String fullText = (a.text() + " " + a.closest("tr,li,div").text()).toLowerCase();

            // Cerca range anni nel testo: es. "2019 - 2023" o "2019 -"
            int[] range = extractYearRange(fullText);
            if (range == null) continue;

            int from = range[0];
            int to   = range[1]; // 9999 se aperto

            if (anno >= from && anno <= to) {
                // Preferisce generazione con inizio piu' recente (piu' specifica)
                int dist = anno - from;
                if (dist < bestDist) {
                    bestDist = dist;
                    best = href;
                }
            }
        }

        if (best == null) {
            // Fallback: prende la prima generazione listata
            Element first = doc.selectFirst("a[href*='auto-data.net/it/']");
            if (first != null) best = first.absUrl("href");
        }
        return best;
    }

    /**
     * Estrae un range di anni da una stringa di testo.
     * Esempi: "2019 - 2023" -> [2019, 2023]
     *         "2019 -"      -> [2019, 9999]
     *         "dal 2019"    -> [2019, 9999]
     */
    private int[] extractYearRange(String text) {
        // Pattern: 4 cifre che iniziano con 19xx o 20xx
        List<Integer> years = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(19|20)\\d{2}")
            .matcher(text);
        while (m.find()) {
            years.add(Integer.parseInt(m.group()));
        }
        if (years.isEmpty()) return null;
        int from = years.get(0);
        int to   = years.size() > 1 ? years.get(years.size() - 1) : 9999;
        return new int[]{from, to};
    }

    // -----------------------------------------------
    // LIVELLO 4: MOTORIZZAZIONE
    // -----------------------------------------------

    private String findMotorizzazioneUrl(String genUrl, String motore, int anno) throws IOException {
        Document doc = get(genUrl);
        String motoreLower = motore.toLowerCase();

        // Estrae parole chiave dal motore: "2.0 TDI 150CV" -> ["tdi", "150", "2.0"]
        String[] parts = motoreLower.split("[^a-z0-9.]+");

        List<LinkEntry> candidates = new ArrayList<>();
        for (Element a : doc.select("a[href*='auto-data.net/it/']")) {
            String href = a.absUrl("href");
            if (href.contains("-brand-") || href.contains("-models-")) continue;
            if (href.equals(genUrl)) continue;
            String name = a.text().toLowerCase().trim();
            if (!name.isBlank()) {
                candidates.add(new LinkEntry(name, href));
            }
        }

        log.debug("[AutoDataNet] Motorizzazioni trovate: {}", candidates.size());
        if (candidates.isEmpty()) return null;

        // Score: conta quante parti del motore sono presenti nel nome del link
        String bestUrl = null;
        int bestScore = -1;
        for (LinkEntry c : candidates) {
            int score = 0;
            for (String part : parts) {
                if (part.length() >= 2 && c.name().contains(part)) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                bestUrl = c.url();
            }
        }
        return bestUrl;
    }

    // -----------------------------------------------
    // SCHEDA TECNICA
    // -----------------------------------------------

    private Optional<String> fetchSchedaTecnica(String url) throws IOException {
        log.info("[AutoDataNet] Scarico scheda tecnica: {}", url);
        Document doc = get(url);

        // Rimuove elementi non utili
        doc.select("nav, header, footer, script, style, iframe, .ads, .cookie").remove();

        // auto-data.net usa tabelle per i dati tecnici
        StringBuilder sb = new StringBuilder();
        sb.append("[FONTE: ").append(url).append("]\n");

        // Titolo pagina
        String title = doc.title();
        if (!title.isBlank()) sb.append(title).append("\n\n");

        // Tutte le tabelle con dati tecnici
        Elements tables = doc.select("table");
        for (Element table : tables) {
            for (Element row : table.select("tr")) {
                Elements cells = row.select("td, th");
                if (cells.size() >= 2) {
                    sb.append(cells.get(0).text().trim())
                      .append(": ")
                      .append(cells.get(1).text().trim())
                      .append("\n");
                }
            }
            sb.append("\n");
        }

        // Se le tabelle sono vuote, prende tutto il body
        if (sb.length() < 200) {
            Element body = doc.body();
            if (body != null) sb.append(body.text());
        }

        String result = sb.toString();
        log.debug("[AutoDataNet] Scheda tecnica: {} chars", result.length());
        return result.length() > 100 ? Optional.of(result) : Optional.empty();
    }

    // -----------------------------------------------
    // UTILITY
    // -----------------------------------------------

    /**
     * Trova il link con il testo piu' simile al target.
     * Prima cerca match esatto, poi parziale, poi similarity.
     */
    private String bestMatch(String target, List<LinkEntry> candidates) {
        if (candidates.isEmpty()) return null;

        // 1. Match esatto
        for (LinkEntry c : candidates) {
            if (c.name().equals(target)) return c.url();
        }
        // 2. Match parziale
        for (LinkEntry c : candidates) {
            if (c.name().contains(target) || target.contains(c.name())) return c.url();
        }
        // 3. Match per parole: conta parole in comune
        String[] targetWords = target.split("\\s+");
        String bestUrl = null;
        int bestScore = 0;
        for (LinkEntry c : candidates) {
            int score = 0;
            for (String word : targetWords) {
                if (word.length() > 1 && c.name().contains(word)) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                bestUrl = c.url();
            }
        }
        // 4. Fallback: primo risultato
        return bestUrl != null ? bestUrl : candidates.get(0).url();
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

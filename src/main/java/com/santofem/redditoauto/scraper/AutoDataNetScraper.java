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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scraper per auto-data.net - struttura URL verificata con curl:
 *
 * Livello 1: /it/allbrands
 *   -> link formato: /it/volkswagen-brand-80   (contiene -brand-)
 *
 * Livello 2: /it/volkswagen-brand-80
 *   -> link: <a class="modeli" href="/it/volkswagen-passat-model-902">  (contiene -model-)
 *
 * Livello 3: /it/volkswagen-passat-model-902
 *   -> link: <a href="/it/volkswagen-passat-b8-facelift-2019-generation-7177">
 *      anni estratti da: <strong class="end">2019 - 2021</strong>
 *                        <strong class="cur">2023 - </strong>
 *
 * Livello 4: /it/volkswagen-passat-b8-facelift-2019-generation-7177
 *   -> link: <a href="/it/volkswagen-passat-b8-facelift-2019-2.0-tdi-150hp-dsg-39222">
 *      (non contengono -generation-, -model-, -brand-; terminano con -NUMERO)
 *
 * Livello 5: /it/volkswagen-passat-b8-facelift-2019-2.0-tdi-150hp-dsg-39222
 *   -> tabella HTML con dati tecnici
 *
 * MATCHING MOTORIZZAZIONE (Livello 4):
 *   auto-data.net esprime la potenza in HP negli URL (es. 150hp, 200hp).
 *   Il frontend invia la potenza in CV (es. 150CV, 150cv).
 *   CV e HP sono numericamente identici per i valori comuni usati in Italia
 *   (differenza < 2%), percio' il numero viene usato direttamente per il match.
 *   Algoritmo di scoring:
 *     +3  match esatto della potenza (numero HP/CV nell'URL)
 *     +2  match tipo carburante (tdi, tsi, tfsi, gdi, crdi, gte, phev...)
 *     +1  match displacement (2.0, 1.6, 1.4...)
 *     +1  match tipo cambio (dsg, cvt, automatico, manuale)
 *   In caso di parita', vince il candidato con URL piu' simile al motore richiesto.
 */
@Component
@Slf4j
public class AutoDataNetScraper {

    private static final String BASE_IT = "https://www.auto-data.net/it";
    private static final Pattern ENDS_WITH_NUMBER = Pattern.compile("-\\d+$");
    private static final Pattern YEAR_PATTERN    = Pattern.compile("(20|19)\\d{2}");
    private static final Pattern POWER_PATTERN   = Pattern.compile("(\\d{2,4})\\s*(?:cv|hp|kw)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DISPLACEMENT_PATTERN = Pattern.compile("(\\d\\.\\d)");

    // Tipi carburante/motorizzazione riconoscibili nell'URL auto-data.net
    private static final List<String> FUEL_TOKENS = List.of(
        "tdi", "tsi", "tfsi", "gdi", "crdi", "cdti", "jtd", "hdi",
        "gte", "phev", "hybrid", "ibrido", "elettrico", "electric",
        "mpi", "fsi", "gpl", "cng", "etg", "tce", "puretech"
    );

    // Tipi cambio riconoscibili
    private static final List<String> GEARBOX_TOKENS = List.of(
        "dsg", "dct", "cvt", "automatic", "automatico", "manual", "manuale",
        "pdk", "edct", "xdrive", "quattro"
    );

    @Value("${scraper.timeout-ms:10000}")
    private int timeoutMs;

    @Value("${scraper.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36}")
    private String userAgent;

    private final Map<String, String> brandCache = new ConcurrentHashMap<>();
    private volatile Instant brandCacheTime = Instant.EPOCH;
    private static final long CACHE_TTL_SECONDS = 86400;

    // ═══════════════════════════════════════════════
    //  ENTRY POINT
    // ═══════════════════════════════════════════════

    public Optional<String> scrape(String marca, String modello, String motore, int anno) {
        log.info("[AutoDataNet] Navigazione: {} {} {} {}", marca, modello, motore, anno);
        try {
            String brandUrl = findBrandUrl(marca);
            if (brandUrl == null) {
                log.warn("[AutoDataNet] Marca non trovata: {}", marca);
                return Optional.empty();
            }
            log.debug("[AutoDataNet] Livello 1 - Marca: {}", brandUrl);

            String modelUrl = findModelUrl(brandUrl, modello);
            if (modelUrl == null) {
                log.warn("[AutoDataNet] Modello non trovato: {} su {}", modello, brandUrl);
                return Optional.empty();
            }
            log.debug("[AutoDataNet] Livello 2 - Modello: {}", modelUrl);

            String genUrl = findGenerazioneUrl(modelUrl, anno);
            if (genUrl == null) {
                log.warn("[AutoDataNet] Generazione non trovata per anno {} su {}", anno, modelUrl);
                return Optional.empty();
            }
            log.debug("[AutoDataNet] Livello 3 - Generazione: {}", genUrl);

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
    //  LIVELLO 1 - MARCA  (cache 24h)
    // ═══════════════════════════════════════════════

    private String findBrandUrl(String marca) throws IOException {
        refreshBrandCacheIfNeeded();
        String ml = normalize(marca);
        if (brandCache.containsKey(ml)) return brandCache.get(ml);
        return brandCache.entrySet().stream()
                .filter(e -> e.getKey().contains(ml) || ml.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);
    }

    private void refreshBrandCacheIfNeeded() throws IOException {
        long elapsed = Instant.now().getEpochSecond() - brandCacheTime.getEpochSecond();
        if (elapsed < CACHE_TTL_SECONDS && !brandCache.isEmpty()) return;

        log.debug("[AutoDataNet] Refresh cache marche da /allbrands");
        Document doc = fetch(BASE_IT + "/allbrands");
        for (Element a : doc.select("a[href*=-brand-]")) {
            String href = absoluteHref(a);
            String name = normalize(a.text());
            if (!href.isEmpty() && !name.isEmpty()) brandCache.put(name, href);
        }
        brandCacheTime = Instant.now();
        log.debug("[AutoDataNet] Cache marche: {} voci", brandCache.size());
    }

    // ═══════════════════════════════════════════════
    //  LIVELLO 2 - MODELLO
    // ═══════════════════════════════════════════════

    private String findModelUrl(String brandUrl, String modello) throws IOException {
        Document doc = fetch(brandUrl);
        String ml = normalize(modello);

        List<LinkEntry> candidates = new ArrayList<>();
        for (Element a : doc.select("a.modeli[href]")) {
            String href = absoluteHref(a);
            if (href.isEmpty() || !href.contains("-model-")) continue;
            Element strong = a.selectFirst("strong");
            String name = normalize(strong != null ? strong.text() : a.text());
            if (!name.isEmpty()) candidates.add(new LinkEntry(name, href));
        }

        log.debug("[AutoDataNet] Modelli trovati: {} | es: {}",
                candidates.size(),
                candidates.isEmpty() ? "nessuno" : candidates.get(0).name() + " -> " + candidates.get(0).url());

        return bestMatch(ml, candidates);
    }

    // ═══════════════════════════════════════════════
    //  LIVELLO 3 - GENERAZIONE
    // ═══════════════════════════════════════════════

    private String findGenerazioneUrl(String modelUrl, int anno) throws IOException {
        Document doc = fetch(modelUrl);

        String bestUrl  = null;
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

            int from = range[0];
            int to   = range[1];

            if (anno >= from && anno <= to) {
                int score = from;
                if (score > bestScore) { bestScore = score; bestUrl = href; }
            }
        }

        if (bestUrl == null && fallback != null) {
            log.debug("[AutoDataNet] Generazione: nessun match per anno {}, uso prima disponibile: {}", anno, fallback);
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
        int from = years.get(0);
        int to   = years.size() > 1 ? years.get(years.size() - 1) : 9999;
        return new int[]{from, to};
    }

    // ═══════════════════════════════════════════════
    //  LIVELLO 4 - MOTORIZZAZIONE  (scoring migliorato)
    // ═══════════════════════════════════════════════

    private String findMotorizzazioneUrl(String genUrl, String motore) throws IOException {
        Document doc = fetch(genUrl);
        String ml = normalize(motore);

        // Estrai tokens significativi dal motore richiesto
        int    requestedPower        = extractPower(ml);          // es. 150 (da "150cv" o "150hp")
        String requestedDisplacement = extractDisplacement(ml);   // es. "2.0"
        List<String> requestedFuel   = matchingTokens(ml, FUEL_TOKENS);     // es. ["tdi"]
        List<String> requestedGearbox= matchingTokens(ml, GEARBOX_TOKENS);  // es. ["dsg"]

        log.debug("[AutoDataNet] Matching motore '{}': potenza={} displacement={} fuel={} gearbox={}",
            ml, requestedPower, requestedDisplacement, requestedFuel, requestedGearbox);

        List<LinkEntry> candidates = new ArrayList<>();
        for (Element a : doc.select("a[href]")) {
            String href = absoluteHref(a);
            if (href.isEmpty()) continue;
            if (!href.startsWith("https://www.auto-data.net/it/")) continue;
            if (href.contains("-generation-") || href.contains("-model-") ||
                href.contains("-brand-")       || href.endsWith("/it/") ||
                href.contains("/login")         || href.contains("/search") ||
                href.contains("/register")      || href.contains("/allbrands")) continue;
            if (!ENDS_WITH_NUMBER.matcher(href).find()) continue;
            if (href.equals(genUrl)) continue;

            Element tit  = a.selectFirst(".tit");
            String  name = normalize(tit != null ? tit.text() : a.text());
            if (!name.isEmpty()) candidates.add(new LinkEntry(name, href));
        }

        // Deduplicazione per href
        Map<String, LinkEntry> dedup = new LinkedHashMap<>();
        for (LinkEntry e : candidates) dedup.put(e.url(), e);
        candidates = new ArrayList<>(dedup.values());

        log.debug("[AutoDataNet] Motorizzazioni trovate: {} | es: {}",
                candidates.size(),
                candidates.isEmpty() ? "nessuna" : candidates.get(0).name() + " -> " + candidates.get(0).url());

        if (candidates.isEmpty()) return null;

        // ── SCORING PESATO ──────────────────────────────────────────────
        // Pesi:
        //   +3  match potenza numerica (150cv == 150hp nell'URL)
        //   +2  match tipo carburante/motorizzazione (tdi, tsi, ecc.)
        //   +1  match displacement (2.0, 1.6, ecc.)
        //   +1  match tipo cambio (dsg, cvt, ecc.)
        // ────────────────────────────────────────────────────────────────
        String bestUrl   = null;
        int    bestScore = -1;

        for (LinkEntry c : candidates) {
            String urlLower = c.url().toLowerCase();
            int score = 0;

            // +3 potenza: l'URL contiene il numero HP che coincide con CV richiesto
            if (requestedPower > 0) {
                String powerToken = requestedPower + "hp";
                if (urlLower.contains(powerToken) || c.name().contains(String.valueOf(requestedPower))) {
                    score += 3;
                }
            }

            // +2 tipo carburante
            for (String ft : requestedFuel) {
                if (urlLower.contains(ft) || c.name().contains(ft)) { score += 2; break; }
            }

            // +1 displacement
            if (requestedDisplacement != null) {
                if (urlLower.contains(requestedDisplacement) || c.name().contains(requestedDisplacement)) {
                    score += 1;
                }
            }

            // +1 cambio
            for (String gt : requestedGearbox) {
                if (urlLower.contains(gt) || c.name().contains(gt)) { score += 1; break; }
            }

            log.debug("[AutoDataNet] Candidato score={}: {} -> {}", score, c.name(), c.url());

            if (score > bestScore) { bestScore = score; bestUrl = c.url(); }
        }

        log.debug("[AutoDataNet] Miglior match (score={}): {}", bestScore, bestUrl);
        return bestUrl != null ? bestUrl : candidates.get(0).url();
    }

    // ═══════════════════════════════════════════════
    //  LIVELLO 5 - SCHEDA TECNICA
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
                    if (!label.isEmpty() && !value.isEmpty()) {
                        sb.append(label).append(": ").append(value).append("\n");
                    }
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
    //  UTILITY MATCHING
    // ═══════════════════════════════════════════════

    /**
     * Estrae il numero di potenza da una stringa motore.
     * Esempi: "2.0 TDI 150CV DSG" -> 150
     *         "110kw" -> 110
     *         "200hp" -> 200
     * Se non trovato, ritorna 0.
     */
    private int extractPower(String motore) {
        Matcher m = POWER_PATTERN.matcher(motore);
        while (m.find()) {
            try {
                int val = Integer.parseInt(m.group(1));
                // Filtra: potenza auto realistica tra 40 e 700
                if (val >= 40 && val <= 700) return val;
            } catch (NumberFormatException ignored) {}
        }
        // Fallback: cerca qualsiasi numero isolato tra 40 e 700
        Matcher fallback = Pattern.compile("(?<![.\\d])(\\d{2,3})(?![.\\d])").matcher(motore);
        while (fallback.find()) {
            try {
                int val = Integer.parseInt(fallback.group(1));
                if (val >= 40 && val <= 700) return val;
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    /**
     * Estrae il displacement (cilindrata in litri) da una stringa motore.
     * Esempi: "2.0 TDI" -> "2.0", "1.6 MPI" -> "1.6"
     */
    private String extractDisplacement(String motore) {
        Matcher m = DISPLACEMENT_PATTERN.matcher(motore);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Trova quali token della lista sono presenti nella stringa motore.
     */
    private List<String> matchingTokens(String motore, List<String> tokens) {
        List<String> found = new ArrayList<>();
        for (String t : tokens) {
            if (motore.contains(t)) found.add(t);
        }
        return found;
    }

    /** Matching generico: esatto -> parziale -> score per parole */
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
                .referrer("https://www.auto-data.net/it/allbrands")
                .get();
    }

    private record LinkEntry(String name, String url) {}
}

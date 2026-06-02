package com.santofem.redditoauto.scraper;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scraper per auto-data.net.
 *
 * NAVIGAZIONE 4 LIVELLI:
 *   1) /allbrands  → link /it/<marca>-brand-<id>    (cache in memoria)
 *   2) /it/<marca>-brand-<id>  → link /it/<marca>-<modello>-model-<id>
 *   3) /it/<marca>-<modello>-model-<id>  → link /it/<marca>-<gen>-generation-<id>  (match anno)
 *   4) /it/<marca>-<gen>-generation-<id> → link motorizzazione  (scoring cv/carburante/cilindrata)
 *   5) Scheda tecnica → testo tabelle
 *
 * FUZZY MATCHING:
 *   - Marca: normalizzazione + confronto token per token
 *   - Modello: split spazio, match token, best overlap (gestisce "ttrs" → "tt rs")
 *   - Motorizzazione: +3 potenza, +2 carburante, +1 cilindrata, +1 cambio
 */
@Component
@Slf4j
public class AutoDataNetScraper {

    private static final String BASE          = "https://www.auto-data.net";
    private static final String ALL_BRANDS    = BASE + "/it/allbrands";

    private static final Pattern YEAR_PAT     = Pattern.compile("(20|19)\\d{2}");
    private static final Pattern POWER_PAT    = Pattern.compile("(\\d{2,4})\\s*(?:cv|hp|kw)", Pattern.CASE_INSENSITIVE);
    private static final Pattern POWER_NUM    = Pattern.compile("(?<![.\\d])(\\d{2,3})(?![.\\d])");
    private static final Pattern DISP_PAT     = Pattern.compile("(\\d[.,]\\d)");
    private static final Pattern ENDS_NUM     = Pattern.compile("-\\d+$");

    private static final List<String> FUEL_TOKENS = List.of(
        "tdi", "tsi", "tfsi", "gdi", "crdi", "cdti", "jtd", "hdi",
        "gte", "phev", "hybrid", "ibrido", "mhev", "mild",
        "mpi", "fsi", "gpl", "cng", "tce", "puretech", "bluehdi",
        "spce", "etorq", "multijet", "dci", "vtec", "skyactiv"
    );
    private static final List<String> GEAR_TOKENS = List.of(
        "dsg", "dct", "cvt", "automatic", "automatico", "pdk",
        "edct", "s-tronic", "stronic", "xtronic", "tiptronic"
    );

    // Cache brand: "volkswagen" -> "https://www.auto-data.net/it/volkswagen-brand-80"
    private final Map<String, String> brandCache = new ConcurrentHashMap<>();
    private volatile long brandCacheTime = 0;
    private static final long CACHE_TTL_MS = 6 * 60 * 60 * 1000L; // 6 ore

    @Value("${scraper.timeout-ms:10000}")
    private int timeoutMs;

    @Value("${scraper.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36}")
    private String userAgent;

    // ══════════════════════════════════════════════
    //  ENTRY POINT
    // ══════════════════════════════════════════════

    public Optional<String> scrape(String marca, String modello, String motore, int anno) {
        log.info("[AutoDataNet] Navigazione: {} {} {} {}", marca, modello, motore, anno);
        try {
            // LIVELLO 1: brand URL
            String brandUrl = findBrandUrl(marca);
            if (brandUrl == null) {
                log.warn("[AutoDataNet] Marca non trovata: {}", marca);
                return Optional.empty();
            }
            log.debug("[AutoDataNet] Livello 1 - Marca: {}", brandUrl);

            // LIVELLO 2: model URL
            String modelUrl = findModelUrl(brandUrl, modello);
            if (modelUrl == null) {
                log.warn("[AutoDataNet] Modello non trovato: {} su {}", modello, brandUrl);
                return Optional.empty();
            }
            log.debug("[AutoDataNet] Livello 2 - Modello: {}", modelUrl);

            // LIVELLO 3: generazione
            String genUrl = findGenerazioneUrl(modelUrl, anno);
            if (genUrl == null) {
                log.warn("[AutoDataNet] Generazione non trovata per anno {}", anno);
                return Optional.empty();
            }
            log.debug("[AutoDataNet] Livello 3 - Generazione: {}", genUrl);

            // LIVELLO 4: motorizzazione
            String motorUrl = findMotorizzazioneUrl(genUrl, motore);
            if (motorUrl == null) {
                log.warn("[AutoDataNet] Motorizzazione non trovata: {}", motore);
                return Optional.empty();
            }
            log.debug("[AutoDataNet] Livello 4 - Motorizzazione: {}", motorUrl);

            return fetchSchedaTecnica(motorUrl);

        } catch (Exception e) {
            log.warn("[AutoDataNet] Errore: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ══════════════════════════════════════════════
    //  LIVELLO 1 — BRAND
    // ══════════════════════════════════════════════

    private String findBrandUrl(String marca) throws IOException {
        refreshBrandCacheIfNeeded();
        String key = normalize(marca);

        // Exact match
        if (brandCache.containsKey(key)) return brandCache.get(key);

        // Fuzzy: la marca dell'utente è contenuta nella chiave cache o viceversa
        for (Map.Entry<String, String> e : brandCache.entrySet()) {
            if (e.getKey().contains(key) || key.contains(e.getKey())) {
                log.debug("[AutoDataNet] Fuzzy brand match: '{}' -> '{}'", key, e.getKey());
                return e.getValue();
            }
        }

        // Token match: "mercedes benz" -> chiave "mercedes-benz"
        String keyDash = key.replace(" ", "-");
        if (brandCache.containsKey(keyDash)) return brandCache.get(keyDash);

        log.warn("[AutoDataNet] Brand cache ({} voci) non contiene '{}'", brandCache.size(), key);
        return null;
    }

    private void refreshBrandCacheIfNeeded() throws IOException {
        long now = System.currentTimeMillis();
        if (!brandCache.isEmpty() && (now - brandCacheTime) < CACHE_TTL_MS) return;

        log.debug("[AutoDataNet] Refresh cache marche da /allbrands");
        Document doc = fetch(ALL_BRANDS);
        Map<String, String> fresh = new LinkedHashMap<>();

        for (Element a : doc.select("a[href*=-brand-]")) {
            String href = absoluteHref(a);
            if (href.isEmpty()) continue;
            // Ricava il nome marca dal testo del link o dall'URL
            String name = normalize(a.text());
            if (name.isEmpty()) {
                // fallback: estrai dalla URL "/it/volkswagen-brand-80" -> "volkswagen"
                String path = href.replaceAll(".*/it/", "").replaceAll("-brand-\\d+.*", "");
                name = normalize(path);
            }
            if (!name.isEmpty()) fresh.put(name, href);
        }

        if (!fresh.isEmpty()) {
            brandCache.clear();
            brandCache.putAll(fresh);
            brandCacheTime = now;
            log.debug("[AutoDataNet] Cache marche: {} voci", brandCache.size());
        } else {
            log.warn("[AutoDataNet] /allbrands: nessun link -brand- trovato (probabile JS rendering)");
        }
    }

    // ══════════════════════════════════════════════
    //  LIVELLO 2 — MODELLO
    // ══════════════════════════════════════════════

    private String findModelUrl(String brandUrl, String modello) throws IOException {
        Document doc = fetch(brandUrl);
        List<LinkEntry> candidates = new ArrayList<>();

        for (Element a : doc.select("a[href*=-model-]")) {
            String href = absoluteHref(a);
            if (href.isEmpty()) continue;
            String name = normalize(a.text());
            if (name.isEmpty()) name = normalize(href.replaceAll(".*/it/", "").replaceAll("-model-\\d+.*", "").replace("-", " "));
            if (!name.isEmpty()) candidates.add(new LinkEntry(name, href));
        }

        // Dedup
        Map<String, LinkEntry> dedup = new LinkedHashMap<>();
        for (LinkEntry e : candidates) dedup.put(e.url(), e);
        candidates = new ArrayList<>(dedup.values());

        log.debug("[AutoDataNet] Modelli trovati: {} | es: {}",
            candidates.size(),
            candidates.isEmpty() ? "nessuno" : candidates.get(0).name() + " -> " + candidates.get(0).url());

        return bestModelMatch(normalize(modello), candidates);
    }

    /**
     * Match modello con gestione casi speciali:
     *   "ttrs"  -> cerca "tt rs", "tt-rs", "tt" con sub-link "rs"
     *   "rs4"   -> "rs 4", "rs4"
     *   "classe a" -> "classe a", "a-class"
     *
     * Strategia:
     *   1. Match esatto normalizzato
     *   2. Match senza spazi ("tt rs" == "ttrs")
     *   3. Match per token: ogni token del modello deve apparire nel candidato
     *   4. Best overlap conteggio token
     */
    private String bestModelMatch(String modello, List<LinkEntry> candidates) {
        if (candidates.isEmpty()) return null;
        String mNorm = modello.replace("-", " ").trim();
        String mCompact = mNorm.replace(" ", ""); // "ttrs" o "rs4" o "classea"

        // 1. Exact
        for (LinkEntry c : candidates)
            if (c.name().replace("-", " ").equals(mNorm)) return c.url();

        // 2. Compact match ("ttrs" vs "tt rs" -> compact entrambi)
        for (LinkEntry c : candidates)
            if (c.name().replace("-", " ").replace(" ", "").equals(mCompact)) return c.url();

        // 3. Il candidato contiene tutti i token del modello
        String[] tokens = mNorm.split("\\s+");
        for (LinkEntry c : candidates) {
            String cn = c.name().replace("-", " ");
            boolean allMatch = true;
            for (String t : tokens) if (!cn.contains(t)) { allMatch = false; break; }
            if (allMatch) return c.url();
        }

        // 4. Best overlap
        String best = null; int bestScore = 0;
        for (LinkEntry c : candidates) {
            String cn = c.name().replace("-", " ");
            int score = 0;
            for (String t : tokens) if (t.length() > 1 && cn.contains(t)) score++;
            // bonus: il candidato inizia con lo stesso prefisso compatto
            if (cn.replace(" ", "").startsWith(mCompact.substring(0, Math.min(3, mCompact.length())))) score++;
            if (score > bestScore) { bestScore = score; best = c.url(); }
        }
        return best;
    }

    // ══════════════════════════════════════════════
    //  LIVELLO 3 — GENERAZIONE
    // ══════════════════════════════════════════════

    private String findGenerazioneUrl(String modelUrl, int anno) throws IOException {
        Document doc = fetch(modelUrl);

        String bestUrl   = null;
        int    bestFrom  = Integer.MIN_VALUE;
        String fallback  = null;

        for (Element a : doc.select("a[href*=-generation-]")) {
            String href = absoluteHref(a);
            if (href.isEmpty()) continue;
            if (fallback == null) fallback = href;

            // Cerca anni nel contesto (testo del link + elemento padre)
            Element parent = a.closest("div, li, tr");
            String ctx = (parent != null ? parent.text() : "") + " " + a.text();
            int[] range = extractYearRange(ctx);
            if (range == null) continue;

            int from = range[0], to = range[1];
            log.debug("[AutoDataNet] Gen candidate {}-{}: {}", from, to, href);
            if (anno >= from && anno <= to && from > bestFrom) {
                bestFrom = from;
                bestUrl  = href;
            }
        }

        if (bestUrl == null) {
            log.debug("[AutoDataNet] Nessuna gen match per anno {}, uso prima: {}", anno, fallback);
            return fallback;
        }
        return bestUrl;
    }

    private int[] extractYearRange(String text) {
        List<Integer> years = new ArrayList<>();
        Matcher m = YEAR_PAT.matcher(text);
        while (m.find()) years.add(Integer.parseInt(m.group()));
        if (years.isEmpty()) return null;
        Collections.sort(years);
        return new int[]{ years.get(0), years.size() > 1 ? years.get(years.size() - 1) : 2099 };
    }

    // ══════════════════════════════════════════════
    //  LIVELLO 4 — MOTORIZZAZIONE (scoring)
    // ══════════════════════════════════════════════

    private String findMotorizzazioneUrl(String genUrl, String motore) throws IOException {
        Document doc = fetch(genUrl);
        String ml = normalize(motore);

        int    reqPower  = extractPower(ml);
        String reqDisp   = extractDisplacement(ml);
        List<String> reqFuel = matchTokens(ml, FUEL_TOKENS);
        List<String> reqGear = matchTokens(ml, GEAR_TOKENS);

        log.debug("[AutoDataNet] Motor match '{}': cv={} disp={} fuel={} gear={}",
            ml, reqPower, reqDisp, reqFuel, reqGear);

        List<LinkEntry> candidates = new ArrayList<>();
        for (Element a : doc.select("a[href]")) {
            String href = absoluteHref(a);
            if (!isMotorUrl(href, genUrl)) continue;
            String name = normalize(a.text());
            if (name.isEmpty()) name = normalize(href.replaceAll(".*/it/", "").replace("-", " ").replaceAll("\\d+$", "").trim());
            if (!name.isEmpty()) candidates.add(new LinkEntry(name, href));
        }

        // Dedup
        Map<String, LinkEntry> dedup = new LinkedHashMap<>();
        for (LinkEntry e : candidates) dedup.put(e.url(), e);
        candidates = new ArrayList<>(dedup.values());

        log.debug("[AutoDataNet] Motorizzazioni trovate: {} | es: {}",
            candidates.size(),
            candidates.isEmpty() ? "nessuna" : candidates.get(0).name() + " -> " + candidates.get(0).url());

        if (candidates.isEmpty()) return null;

        String bestUrl = null; int bestScore = -1;
        for (LinkEntry c : candidates) {
            String urlL = c.url().toLowerCase();
            String nameL = c.name();
            int score = 0;

            if (reqPower > 0) {
                if (urlL.contains(reqPower + "hp") || nameL.contains(String.valueOf(reqPower))) score += 3;
            }
            for (String ft : reqFuel)
                if (urlL.contains(ft) || nameL.contains(ft)) { score += 2; break; }
            if (reqDisp != null) {
                String dispNorm = reqDisp.replace(",", ".");
                if (urlL.contains(dispNorm) || nameL.contains(dispNorm)) score += 1;
            }
            for (String gt : reqGear)
                if (urlL.contains(gt) || nameL.contains(gt)) { score += 1; break; }

            log.debug("[AutoDataNet] Motor score={}: {} -> {}", score, nameL, c.url());
            if (score > bestScore) { bestScore = score; bestUrl = c.url(); }
        }

        log.debug("[AutoDataNet] Best motor (score={}): {}", bestScore, bestUrl);
        return bestUrl != null ? bestUrl : candidates.get(0).url();
    }

    private boolean isMotorUrl(String href, String genUrl) {
        if (href == null || href.isBlank()) return false;
        if (!href.startsWith(BASE + "/it/")) return false;
        if (href.contains("-generation-") || href.contains("-model-") ||
            href.contains("-brand-") || href.contains("/login") ||
            href.contains("/register") || href.contains("/allbrands") ||
            href.contains("/results") || href.equals(genUrl)) return false;
        return ENDS_NUM.matcher(href).find();
    }

    // ══════════════════════════════════════════════
    //  LIVELLO 5 — SCHEDA TECNICA
    // ══════════════════════════════════════════════

    private Optional<String> fetchSchedaTecnica(String url) throws IOException {
        log.info("[AutoDataNet] Scarico scheda tecnica: {}", url);
        Document doc = fetch(url);
        doc.select("nav, header, footer, script, style, iframe, .ad970, .ads, .cookie").remove();

        StringBuilder sb = new StringBuilder();
        sb.append("[FONTE: ").append(url).append("]\n");
        sb.append(doc.title()).append("\n\n");

        // Estrai righe tabella: Label: Valore
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

        // Fallback: testo body se tabelle insufficienti
        if (sb.length() < 400) {
            Element body = doc.body();
            if (body != null) sb.append(body.text());
        }

        log.info("[AutoDataNet] Scheda estratta: {} chars da {}", sb.length(), url);
        return sb.length() > 200 ? Optional.of(sb.toString()) : Optional.empty();
    }

    // ══════════════════════════════════════════════
    //  UTILITY
    // ══════════════════════════════════════════════

    private int extractPower(String motore) {
        Matcher m = POWER_PAT.matcher(motore);
        while (m.find()) {
            try { int v = Integer.parseInt(m.group(1)); if (v >= 40 && v <= 900) return v; }
            catch (NumberFormatException ignored) {}
        }
        Matcher fb = POWER_NUM.matcher(motore);
        while (fb.find()) {
            try { int v = Integer.parseInt(fb.group(1)); if (v >= 40 && v <= 900) return v; }
            catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private String extractDisplacement(String motore) {
        Matcher m = DISP_PAT.matcher(motore);
        return m.find() ? m.group(1) : null;
    }

    private List<String> matchTokens(String text, List<String> tokens) {
        List<String> found = new ArrayList<>();
        for (String t : tokens) if (text.contains(t)) found.add(t);
        return found;
    }

    private String absoluteHref(Element a) {
        String href = a.attr("href");
        if (href == null || href.isBlank()) return "";
        if (href.startsWith("http")) return href;
        if (href.startsWith("/")) return BASE + href;
        return "";
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase()
                .replaceAll("[àáâã]", "a").replaceAll("[èéêë]", "e")
                .replaceAll("[ìíîï]", "i").replaceAll("[òóôõ]", "o")
                .replaceAll("[ùúûü]", "u").replaceAll("[^a-z0-9 \\-]", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private Document fetch(String url) throws IOException {
        return Jsoup.connect(url)
            .userAgent(userAgent)
            .timeout(timeoutMs)
            .ignoreHttpErrors(true)
            .followRedirects(true)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.8,*/*;q=0.8")
            .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
            .referrer("https://www.auto-data.net/it/")
            .get();
    }

    private record LinkEntry(String name, String url) {}
}

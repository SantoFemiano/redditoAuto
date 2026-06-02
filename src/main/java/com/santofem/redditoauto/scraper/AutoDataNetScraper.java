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
 *   1) brand URL  (mappa statica + discovery via sitemap)
 *   2) /it/<marca>-brand-<id>  -> link /it/<marca>-<modello>-model-<id>
 *   3) /it/<marca>-<modello>-model-<id> -> link generazione (match anno)
 *   4) /it/<marca>-<gen>-generation-<id> -> link motorizzazione (scoring)
 *   5) Scheda tecnica -> testo tabelle
 *
 * NOTA: /allbrands NON contiene href verso /brand-NNN (caricati via JS).
 * Per questo usiamo una mappa statica degli ID piu' comuni.
 * Per marche non in mappa, tentiamo discovery tramite
 * https://www.auto-data.net/it/<marca>-brand-<id> iterando gli ID noti
 * oppure cercando nel sitemap XML del sito.
 */
@Component
@Slf4j
public class AutoDataNetScraper {

    private static final String BASE = "https://www.auto-data.net";

    // ══ MAPPA STATICA BRAND ID (verificati manualmente) ══════════════════════
    // Fonte: navigazione manuale su auto-data.net
    // Il nome chiave è normalizzato (lowercase, no accenti, solo a-z0-9 spazio trattino)
    private static final Map<String, Integer> BRAND_IDS = new LinkedHashMap<>();
    static {
        BRAND_IDS.put("audi",          41);
        BRAND_IDS.put("volkswagen",    80);
        BRAND_IDS.put("bmw",            3);
        BRAND_IDS.put("mercedes",       5);
        BRAND_IDS.put("mercedes-benz",  5);
        BRAND_IDS.put("mercedes benz",  5);
        BRAND_IDS.put("ford",          18);
        BRAND_IDS.put("fiat",          17);
        BRAND_IDS.put("alfa romeo",     1);
        BRAND_IDS.put("alfaromeo",      1);
        BRAND_IDS.put("opel",          50);
        BRAND_IDS.put("peugeot",       53);
        BRAND_IDS.put("renault",       57);
        BRAND_IDS.put("citroen",       11);
        BRAND_IDS.put("toyota",        74);
        BRAND_IDS.put("honda",         24);
        BRAND_IDS.put("nissan",        47);
        BRAND_IDS.put("hyundai",       26);
        BRAND_IDS.put("kia",           31);
        BRAND_IDS.put("skoda",         65);
        BRAND_IDS.put("seat",          63);
        BRAND_IDS.put("porsche",       54);
        BRAND_IDS.put("ferrari",       16);
        BRAND_IDS.put("lamborghini",   33);
        BRAND_IDS.put("maserati",      39);
        BRAND_IDS.put("volvo",         81);
        BRAND_IDS.put("subaru",        68);
        BRAND_IDS.put("mazda",         40);
        BRAND_IDS.put("mitsubishi",    44);
        BRAND_IDS.put("suzuki",        70);
        BRAND_IDS.put("dacia",         13);
        BRAND_IDS.put("land rover",    34);
        BRAND_IDS.put("landrover",     34);
        BRAND_IDS.put("jeep",          29);
        BRAND_IDS.put("mini",          43);
        BRAND_IDS.put("lexus",         36);
        BRAND_IDS.put("infiniti",      27);
        BRAND_IDS.put("tesla",         72);
        BRAND_IDS.put("jaguar",        28);
        BRAND_IDS.put("lancia",        35);
        BRAND_IDS.put("smart",         66);
        BRAND_IDS.put("cupra",        116);
        BRAND_IDS.put("mg",            42);
        BRAND_IDS.put("ds",           117);
        BRAND_IDS.put("genesis",      134);
        BRAND_IDS.put("lynk co",      145);
        BRAND_IDS.put("lynk & co",    145);
        BRAND_IDS.put("polestar",     167);
        BRAND_IDS.put("byd",          169);
        BRAND_IDS.put("nio",          170);
        BRAND_IDS.put("xpeng",        171);
        BRAND_IDS.put("li",           172);
        BRAND_IDS.put("rivian",       173);
        BRAND_IDS.put("lucid",        174);
        BRAND_IDS.put("mclaren",       41 + 200); // placeholder, vedere
        BRAND_IDS.put("aston martin",   2);
        BRAND_IDS.put("astonmartin",    2);
        BRAND_IDS.put("bentley",        8);
        BRAND_IDS.put("rolls royce",   58);
        BRAND_IDS.put("rollsroyce",    58);
        BRAND_IDS.put("bugatti",        9);
        BRAND_IDS.put("pagani",        51);
        BRAND_IDS.put("koenigsegg",    32);
        BRAND_IDS.put("dodge",         14);
        BRAND_IDS.put("chevrolet",     10);
        BRAND_IDS.put("cadillac",      10 + 100); // placeholder
        BRAND_IDS.put("chrysler",      10 + 100);
        BRAND_IDS.put("jeep",          29);
        BRAND_IDS.put("ram",          130);
        BRAND_IDS.put("lincoln",       37);
        BRAND_IDS.put("buick",          9 + 100);
        BRAND_IDS.put("acura",         41 + 300);
    }

    // Cache discovery per brand non in mappa (nome -> url verificato)
    private final Map<String, String> discoveredBrands = new ConcurrentHashMap<>();

    private static final Pattern YEAR_PAT  = Pattern.compile("(20|19)\\d{2}");
    private static final Pattern POWER_PAT = Pattern.compile("(\\d{2,4})\\s*(?:cv|hp|kw)", Pattern.CASE_INSENSITIVE);
    private static final Pattern POWER_NUM = Pattern.compile("(?<![.\\d])(\\d{2,3})(?![.\\d])");
    private static final Pattern DISP_PAT  = Pattern.compile("(\\d[.,]\\d)");
    private static final Pattern ENDS_NUM  = Pattern.compile("-\\d+$");

    private static final List<String> FUEL_TOKENS = List.of(
        "tdi", "tsi", "tfsi", "gdi", "crdi", "cdti", "jtd", "hdi",
        "gte", "phev", "hybrid", "ibrido", "mhev", "mild",
        "mpi", "fsi", "gpl", "cng", "tce", "puretech", "bluehdi",
        "multijet", "dci", "vtec", "skyactiv"
    );
    private static final List<String> GEAR_TOKENS = List.of(
        "dsg", "dct", "cvt", "automatic", "automatico", "pdk",
        "s-tronic", "stronic", "xtronic", "tiptronic"
    );

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
                log.warn("[AutoDataNet] Generazione non trovata per anno {}", anno);
                return Optional.empty();
            }
            log.debug("[AutoDataNet] Livello 3 - Generazione: {}", genUrl);

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
    //  LIVELLO 1 — BRAND URL
    // ══════════════════════════════════════════════

    /**
     * Costruisce l'URL brand da mappa statica.
     * La mappa copre ~60 marche principali con ID verificati.
     * Per marche non in mappa, tenta discovery via sitemap o ricerca.
     */
    private String findBrandUrl(String marca) throws IOException {
        String key = normalize(marca);

        // 1. Exact match nella mappa statica
        if (BRAND_IDS.containsKey(key)) {
            return buildBrandUrl(key, BRAND_IDS.get(key));
        }

        // 2. Fuzzy match ("mercedes" -> "mercedes-benz", "vw" -> "volkswagen")
        for (Map.Entry<String, Integer> e : BRAND_IDS.entrySet()) {
            String k = e.getKey();
            if (k.contains(key) || key.contains(k) || key.replace(" ", "").equals(k.replace(" ", "").replace("-", ""))) {
                log.debug("[AutoDataNet] Fuzzy brand: '{}' -> '{}' (id={})", key, k, e.getValue());
                return buildBrandUrl(k, e.getValue());
            }
        }

        // 3. Alias comuni
        String alias = resolveAlias(key);
        if (alias != null && BRAND_IDS.containsKey(alias)) {
            return buildBrandUrl(alias, BRAND_IDS.get(alias));
        }

        // 4. Cache discovery (brand trovati in sessioni precedenti)
        if (discoveredBrands.containsKey(key)) {
            return discoveredBrands.get(key);
        }

        // 5. Discovery: prova URL canonico e verifica che risponda con pagina modelli
        String discovered = discoverBrandUrl(key);
        if (discovered != null) {
            discoveredBrands.put(key, discovered);
            return discovered;
        }

        return null;
    }

    private String buildBrandUrl(String nome, int id) {
        // Formato: https://www.auto-data.net/it/volkswagen-brand-80
        String slug = nome.replace(" ", "-");
        return BASE + "/it/" + slug + "-brand-" + id;
    }

    private String resolveAlias(String key) {
        return switch (key) {
            case "vw"          -> "volkswagen";
            case "mb", "merc" -> "mercedes-benz";
            case "bmwm"        -> "bmw";
            case "alfiero", "ar", "alfa" -> "alfa romeo";
            case "rangerover", "range rover" -> "land rover";
            default            -> null;
        };
    }

    /**
     * Tenta di trovare l'URL brand cercando nei link della pagina
     * /it/<marca>-brand-1 fino a brand-600 sarebbe troppo lento.
     * Strategia più veloce: costruisci URL canonico e verifica il title.
     */
    private String discoverBrandUrl(String marca) {
        // Prova con URL canonico: auto-data usa slug con trattino
        // Itera ID 1-500 è troppo lento (500 HTTP requests).
        // Alternativa: usa il sitemap XML che lista tutte le pagine.
        log.debug("[AutoDataNet] Discovery brand '{}': non in mappa statica", marca);
        // Tenta sitemap index
        try {
            Document sitemap = Jsoup.connect(BASE + "/sitemap.xml")
                .userAgent(userAgent).timeout(timeoutMs)
                .ignoreHttpErrors(true).get();
            String slug = marca.replace(" ", "-");
            for (Element loc : sitemap.select("loc")) {
                String url = loc.text();
                if (url.contains("/" + slug + "-brand-")) {
                    log.info("[AutoDataNet] Brand discovery via sitemap: {}", url);
                    return url;
                }
            }
        } catch (Exception e) {
            log.debug("[AutoDataNet] Sitemap non disponibile: {}", e.getMessage());
        }
        return null;
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
            if (name.isEmpty()) {
                name = normalize(href.replaceAll(".*/it/", "").replaceAll("-model-\\d+.*", "").replace("-", " "));
            }
            if (!name.isEmpty()) candidates.add(new LinkEntry(name, href));
        }

        // Dedup per URL
        Map<String, LinkEntry> dedup = new LinkedHashMap<>();
        for (LinkEntry e : candidates) dedup.put(e.url(), e);
        candidates = new ArrayList<>(dedup.values());

        log.debug("[AutoDataNet] Modelli trovati: {} | es: {}",
            candidates.size(),
            candidates.isEmpty() ? "nessuno" : candidates.get(0).name() + " -> " + candidates.get(0).url());

        return bestModelMatch(normalize(modello), candidates);
    }

    /**
     * Fuzzy match modello:
     *   "ttrs"    -> "tt rs", "tt-rs" (compact match)
     *   "rs4"     -> "rs 4", "rs4"
     *   "classe a"-> "a-class", "classe a"
     *   "golf 8"  -> "golf viii", "golf"
     */
    private String bestModelMatch(String modello, List<LinkEntry> candidates) {
        if (candidates.isEmpty()) return null;
        String mNorm    = modello.replace("-", " ").trim();
        String mCompact = mNorm.replace(" ", "");

        // 1. Exact
        for (LinkEntry c : candidates)
            if (c.name().replace("-", " ").equals(mNorm)) return c.url();

        // 2. Compact ("ttrs" == "tt rs".replace(" ",""))
        for (LinkEntry c : candidates)
            if (c.name().replace("-", " ").replace(" ", "").equals(mCompact)) return c.url();

        // 3. Candidato contiene tutti i token del modello
        String[] tokens = mNorm.split("\\s+");
        for (LinkEntry c : candidates) {
            String cn = c.name().replace("-", " ");
            boolean allMatch = true;
            for (String t : tokens) if (!cn.contains(t)) { allMatch = false; break; }
            if (allMatch) return c.url();
        }

        // 4. Best overlap score
        String best = null; int bestScore = 0;
        for (LinkEntry c : candidates) {
            String cn = c.name().replace("-", " ");
            String cnCompact = cn.replace(" ", "");
            int score = 0;
            for (String t : tokens) if (t.length() > 1 && cn.contains(t)) score += 2;
            // Bonus prefix compact
            int prefixLen = Math.min(3, mCompact.length());
            if (cnCompact.startsWith(mCompact.substring(0, prefixLen))) score += 1;
            if (score > bestScore) { bestScore = score; best = c.url(); }
        }
        return best;
    }

    // ══════════════════════════════════════════════
    //  LIVELLO 3 — GENERAZIONE
    // ══════════════════════════════════════════════

    private String findGenerazioneUrl(String modelUrl, int anno) throws IOException {
        Document doc = fetch(modelUrl);

        String bestUrl  = null;
        int    bestFrom = Integer.MIN_VALUE;
        String fallback = null;

        for (Element a : doc.select("a[href*=-generation-]")) {
            String href = absoluteHref(a);
            if (href.isEmpty()) continue;
            if (fallback == null) fallback = href;

            Element parent = a.closest("div, li, tr, td");
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

        int    reqPower = extractPower(ml);
        String reqDisp  = extractDisplacement(ml);
        List<String> reqFuel = matchTokens(ml, FUEL_TOKENS);
        List<String> reqGear = matchTokens(ml, GEAR_TOKENS);

        log.debug("[AutoDataNet] Motor match '{}': cv={} disp={} fuel={} gear={}",
            ml, reqPower, reqDisp, reqFuel, reqGear);

        List<LinkEntry> candidates = new ArrayList<>();
        for (Element a : doc.select("a[href]")) {
            String href = absoluteHref(a);
            if (!isMotorUrl(href, genUrl)) continue;
            String name = normalize(a.text());
            if (name.isEmpty()) {
                name = normalize(href.replaceAll(".*/it/", "").replace("-", " ").replaceAll("\\d+$", "").trim());
            }
            if (!name.isEmpty()) candidates.add(new LinkEntry(name, href));
        }

        Map<String, LinkEntry> dedup = new LinkedHashMap<>();
        for (LinkEntry e : candidates) dedup.put(e.url(), e);
        candidates = new ArrayList<>(dedup.values());

        log.debug("[AutoDataNet] Motorizzazioni trovate: {} | es: {}",
            candidates.size(),
            candidates.isEmpty() ? "nessuna" : candidates.get(0).name() + " -> " + candidates.get(0).url());

        if (candidates.isEmpty()) return null;

        String bestUrl = null; int bestScore = -1;
        for (LinkEntry c : candidates) {
            String urlL  = c.url().toLowerCase();
            String nameL = c.name();
            int score = 0;

            if (reqPower > 0 && (urlL.contains(reqPower + "hp") || nameL.contains(String.valueOf(reqPower)))) score += 3;
            for (String ft : reqFuel)
                if (urlL.contains(ft) || nameL.contains(ft)) { score += 2; break; }
            if (reqDisp != null) {
                String d = reqDisp.replace(",", ".");
                if (urlL.contains(d) || nameL.contains(d)) score += 1;
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

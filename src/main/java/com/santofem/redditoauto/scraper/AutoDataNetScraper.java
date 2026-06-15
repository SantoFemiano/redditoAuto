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
 * 1) brand URL  (mappa statica + discovery via sitemap)
 * 2) /it/<marca>-brand-<id>  -> link /it/<marca>-<modello>-model-<id>
 * 3) /it/<marca>-<modello>-model-<id> -> link generazione (match anno)
 * 4) /it/<marca>-<gen>-generation-<id> -> link motorizzazione (scoring)
 * 5) Scheda tecnica -> testo tabelle
 */
@Component
@Slf4j
public class AutoDataNetScraper {

    private static final String BASE = "https://www.auto-data.net";

    // ══ MAPPA STATICA BRAND ID (verificati manualmente) ══════════════════════
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
        BRAND_IDS.put("mclaren",       41 + 200);
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
        BRAND_IDS.put("cadillac",      10 + 100);
        BRAND_IDS.put("chrysler",      10 + 100);
        BRAND_IDS.put("ram",          130);
        BRAND_IDS.put("lincoln",       37);
        BRAND_IDS.put("buick",          9 + 100);
        BRAND_IDS.put("acura",         41 + 300);
    }

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

    private static final Map<String, String> FUEL_ALIAS = Map.of(
            "diesel",   "tdi",
            "benzina",  "tsi",
            "petrol",   "tsi",
            "ibrido",   "hybrid",
            "electric", "bev",
            "elettrico","bev",
            "gpl",      "gpl",
            "metano",   "cng"
    );

    @Value("${scraper.timeout-ms:10000}")
    private int timeoutMs;

    @Value("${scraper.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36}")
    private String userAgent;

    // ══════════════════════════════════════════════
    //  ENTRY POINT
    // ══════════════════════════════════════════════

    public ScraperResult scrapeConRisultato(
            String marca, String modello, String motore, int anno,
            int potenzaCv, String tipoCarburante, String tipoCambio) {
        log.info("[AutoDataNet] Navigazione: {} {} {} {}", marca, modello, motore, anno);
        try {
            String brandUrl = findBrandUrl(marca);
            if (brandUrl == null) {
                log.warn("[AutoDataNet] Marca non trovata: {}", marca);
                return ScraperResult.empty(anno);
            }
            log.debug("[AutoDataNet] Livello 1 - Marca: {}", brandUrl);

            String modelUrl = findModelUrl(brandUrl, modello);
            if (modelUrl == null) {
                log.warn("[AutoDataNet] Modello non trovato: {} su {}", modello, brandUrl);
                return ScraperResult.empty(anno);
            }

            // --- ESTRAZIONE PREFISSO ANTI-CONTAMINAZIONE ---
            String modelPrefix = extractModelPrefix(modelUrl);
            log.debug("[AutoDataNet] Livello 2 - Modello: {} (Prefisso Esatto: {})", modelUrl, modelPrefix);

            GenerazioneResult genResult = findGenerazioneUrlConAnno(modelUrl, anno, modelPrefix);
            if (genResult == null) {
                log.warn("[AutoDataNet] Generazione non trovata per anno {}", anno);
                return ScraperResult.empty(anno);
            }
            log.debug("[AutoDataNet] Livello 3 - Generazione: {} (annoEffettivo={}{})",
                    genResult.url(), genResult.annoEffettivo(),
                    genResult.isFallback() ? " [FALLBACK]" : "");

            String motorUrl = findMotorizzazioneUrl(
                    genResult.url(), motore, potenzaCv, tipoCarburante, tipoCambio, modelPrefix);
            if (motorUrl == null) {
                log.warn("[AutoDataNet] Motorizzazione non trovata: {}", motore);
                return ScraperResult.empty(anno);
            }
            log.debug("[AutoDataNet] Livello 4 - Motorizzazione: {}", motorUrl);

            Optional<String> testo = fetchSchedaTecnica(motorUrl);
            if (testo.isEmpty()) {
                return ScraperResult.empty(anno);
            }

            if (genResult.isFallback()) {
                log.warn("[AutoDataNet] Anno {} non disponibile: usata generazione {}. Anno salvato: {}",
                        anno, genResult.annoEffettivo(), genResult.annoEffettivo());
                return ScraperResult.foundWithFallback(testo.get(), anno, genResult.annoEffettivo());
            }
            return ScraperResult.found(testo.get(), anno);

        } catch (Exception e) {
            log.warn("[AutoDataNet] Errore: {}", e.getMessage());
            return ScraperResult.empty(anno);
        }
    }

    public Optional<String> scrape(String marca, String modello, String motore, int anno) {
        ScraperResult result = scrapeConRisultato(marca, modello, motore, anno, 0, null, null);
        return result.hasText() ? Optional.of(result.testo()) : Optional.empty();
    }

    // ══════════════════════════════════════════════
    //  LIVELLO 1 — BRAND URL
    // ══════════════════════════════════════════════

    private String findBrandUrl(String marca) throws IOException {
        String key = normalize(marca);
        if (BRAND_IDS.containsKey(key)) return buildBrandUrl(key, BRAND_IDS.get(key));

        for (Map.Entry<String, Integer> e : BRAND_IDS.entrySet()) {
            String k = e.getKey();
            if (k.contains(key) || key.contains(k) || key.replace(" ", "").equals(k.replace(" ", "").replace("-", ""))) {
                return buildBrandUrl(k, e.getValue());
            }
        }

        String alias = resolveAlias(key);
        if (alias != null && BRAND_IDS.containsKey(alias)) return buildBrandUrl(alias, BRAND_IDS.get(alias));

        if (discoveredBrands.containsKey(key)) return discoveredBrands.get(key);

        String discovered = discoverBrandUrl(key);
        if (discovered != null) {
            discoveredBrands.put(key, discovered);
            return discovered;
        }

        return null;
    }

    private String buildBrandUrl(String nome, int id) {
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

    private String discoverBrandUrl(String marca) {
        try {
            Document sitemap = Jsoup.connect(BASE + "/sitemap.xml")
                    .userAgent(userAgent).timeout(timeoutMs)
                    .ignoreHttpErrors(true).get();
            String slug = marca.replace(" ", "-");
            for (Element loc : sitemap.select("loc")) {
                String url = loc.text();
                if (url.contains("/" + slug + "-brand-")) return url;
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
            if (!name.isEmpty()) candidates.add(new LinkEntry(name, href, 0, null));
        }

        Map<String, LinkEntry> dedup = new LinkedHashMap<>();
        for (LinkEntry e : candidates) dedup.put(e.url(), e);
        candidates = new ArrayList<>(dedup.values());

        return bestModelMatch(normalize(modello), candidates);
    }

    private String bestModelMatch(String modello, List<LinkEntry> candidates) {
        if (candidates.isEmpty()) return null;
        String mNorm    = modello.replace("-", " ").trim();
        String mCompact = mNorm.replace(" ", "");

        for (LinkEntry c : candidates)
            if (c.name().replace("-", " ").equals(mNorm)) return c.url();

        for (LinkEntry c : candidates)
            if (c.name().replace("-", " ").replace(" ", "").equals(mCompact)) return c.url();

        String[] tokens = mNorm.split("\\s+");

        for (LinkEntry c : candidates) {
            String cn = c.name().replace("-", " ");
            List<String> cnTokens = Arrays.asList(cn.split("\\s+"));
            boolean allMatch = true;
            for (String t : tokens) {
                if (!cnTokens.contains(t)) { allMatch = false; break; }
            }
            if (allMatch) return c.url();
        }

        String best = null;
        int bestScore = 0;

        for (LinkEntry c : candidates) {
            String cn = c.name().replace("-", " ");
            List<String> cnTokens = Arrays.asList(cn.split("\\s+"));
            String cnCompact = cn.replace(" ", "");
            int score = 0;

            for (String t : tokens) {
                if (t.length() >= 1) {
                    if (cnTokens.contains(t)) score += 5;
                    else if (cn.contains(t)) score += 1;
                }
            }

            int prefixLen = Math.min(3, mCompact.length());
            if (cnCompact.startsWith(mCompact.substring(0, prefixLen))) score += 2;

            if (score > bestScore) {
                bestScore = score;
                best = c.url();
            }
        }

        return best;
    }

    /**
     * Ricava il prefisso url identificativo di questo specifico modello (es: "/it/audi-s3-").
     * Ci proteggerà dall'estrarre link ad "audi-rs3" nelle sezioni "Modelli Correlati".
     */
    private String extractModelPrefix(String modelUrl) {
        if (modelUrl == null) return null;
        Matcher m = Pattern.compile("/it/([^/]+)-model-\\d+").matcher(modelUrl);
        if (m.find()) {
            return "/it/" + m.group(1) + "-";
        }
        return null;
    }

    // ══════════════════════════════════════════════
    //  LIVELLO 3 — GENERAZIONE
    // ══════════════════════════════════════════════

    private GenerazioneResult findGenerazioneUrlConAnno(String modelUrl, int anno, String modelPrefix) throws IOException {
        Document doc = fetch(modelUrl);

        String bestUrl    = null;
        int    bestFrom   = Integer.MIN_VALUE;
        int    bestAnnoEff = anno;
        String fallbackUrl = null;
        int    fallbackAnno = anno;

        for (Element a : doc.select("a[href*=-generation-]")) {
            String href = absoluteHref(a);
            if (href.isEmpty()) continue;

            // --- FILTRO ANTI-CONTAMINAZIONE ---
            // Assicura che la generazione appartenga strettamente al modello individuato (es S3 e non RS3)
            if (modelPrefix != null && !href.contains(modelPrefix)) {
                continue;
            }

            if (fallbackUrl == null) {
                fallbackUrl = href;
                Element parent = a.closest("div, li, tr, td");
                String ctx = (parent != null ? parent.text() : "") + " " + a.text();
                int[] range = extractYearRange(ctx);
                if (range != null) fallbackAnno = range[0];
            }

            Element parent = a.closest("div, li, tr, td");
            String ctx = (parent != null ? parent.text() : "") + " " + a.text();
            int[] range = extractYearRange(ctx);
            if (range == null) continue;

            int from = range[0], to = range[1];
            if (anno >= from && anno <= to && from > bestFrom) {
                bestFrom    = from;
                bestUrl     = href;
                bestAnnoEff = anno;
            }
        }

        if (bestUrl != null) return new GenerazioneResult(bestUrl, bestAnnoEff, false);

        if (fallbackUrl != null) return new GenerazioneResult(fallbackUrl, fallbackAnno, true);

        return null;
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
    //  LIVELLO 4 — MOTORIZZAZIONE
    // ══════════════════════════════════════════════

    private String findMotorizzazioneUrl(
            String genUrl, String motore,
            int potenzaCvUtente, String tipoCarburanteUtente, String tipoCambioUtente, String modelPrefix)
            throws IOException {

        Document doc = fetch(genUrl);
        String ml = normalize(motore);

        int reqPower = potenzaCvUtente > 0 ? potenzaCvUtente : extractPower(ml);
        String reqDisp  = extractDisplacement(ml);
        List<String> reqFuel = buildFuelTokens(ml, tipoCarburanteUtente);
        List<String> reqGear = buildGearTokens(ml, tipoCambioUtente);

        List<LinkEntry> candidates = new ArrayList<>();
        for (Element a : doc.select("a[href]")) {
            String href = absoluteHref(a);
            if (!isMotorUrl(href, genUrl)) continue;

            // --- FILTRO ANTI-CONTAMINAZIONE ---
            if (modelPrefix != null && !href.contains(modelPrefix)) {
                continue;
            }

            String rawName = a.text();
            String name = normalize(rawName);

            if (name.isEmpty()) {
                name = normalize(href.replaceAll(".*/it/", "").replace("-", " ").replaceAll("\\d+$", "").trim());
            }

            if (!name.isEmpty()) {
                int cv = extractPower(rawName);
                String cilindrata = extractDisplacement(rawName);

                candidates.add(new LinkEntry(name, href, cv, cilindrata));
            }
        }

        Map<String, LinkEntry> dedup = new LinkedHashMap<>();
        for (LinkEntry e : candidates) dedup.put(e.url(), e);
        candidates = new ArrayList<>(dedup.values());

        if (candidates.isEmpty()) return null;

        String bestUrl = null;
        int bestScore = Integer.MIN_VALUE;

        for (LinkEntry c : candidates) {
            String urlL  = c.url().toLowerCase();
            String nameL = c.name();
            int score = 0;

            // --- Carburante ---
            if (!reqFuel.isEmpty()) {
                boolean fuelMatch = false;
                for (String ft : reqFuel)
                    if (urlL.contains(ft) || nameL.contains(ft)) { fuelMatch = true; break; }
                if (fuelMatch) score += 10;
                else           score -= 5;
            } else {
                for (String ft : reqFuel)
                    if (urlL.contains(ft) || nameL.contains(ft)) { score += 2; break; }
            }

            // --- Potenza CV ---
            if (reqPower > 0) {
                int candCv = c.cv();
                if (candCv == 0) {
                    Matcher hpMatcher = Pattern.compile("(\\d{2,4})hp").matcher(urlL);
                    if (hpMatcher.find()) {
                        try { candCv = Integer.parseInt(hpMatcher.group(1)); } catch (NumberFormatException ignored) {}
                    }
                }

                if (candCv > 0) {
                    int diff = Math.abs(candCv - reqPower);
                    if (diff == 0)        score += 15;
                    else if (diff <= 10)  score += 10;
                    else if (diff <= 20)  score += 5;
                    else if (diff > 40)   score -= 20;
                } else if (nameL.contains(String.valueOf(reqPower))) {
                    score += 3;
                }
            }

            // --- Cilindrata ---
            if (reqDisp != null) {
                String d = reqDisp.replace(",", ".");
                if (c.cilindrata() != null && c.cilindrata().equals(d)) {
                    score += 3;
                } else if (urlL.contains(d) || nameL.contains(d)) {
                    score += 1;
                }
            }

            // --- Cambio ---
            if (!reqGear.isEmpty()) {
                for (String gt : reqGear)
                    if (urlL.contains(gt) || nameL.contains(gt)) { score += 2; break; }
            } else {
                for (String gt : reqGear)
                    if (urlL.contains(gt) || nameL.contains(gt)) { score += 1; break; }
            }

            log.debug("[AutoDataNet] Motor score={}: {} -> {}", score, nameL, c.url());
            if (score > bestScore) { bestScore = score; bestUrl = c.url(); }
        }

        if (bestScore <= -10) {
            log.warn("[AutoDataNet] Nessun motor con score accettabile (best={}), uso primo candidato", bestScore);
            return candidates.get(0).url();
        }

        return bestUrl != null ? bestUrl : candidates.get(0).url();
    }

    private List<String> buildFuelTokens(String motoreNorm, String tipoCarburanteUtente) {
        List<String> tokens = new ArrayList<>(matchTokens(motoreNorm, FUEL_TOKENS));
        if (tipoCarburanteUtente != null && !tipoCarburanteUtente.isBlank()) {
            String alias = FUEL_ALIAS.get(tipoCarburanteUtente.toLowerCase().trim());
            if (alias != null && !tokens.contains(alias)) {
                tokens.add(0, alias);
            }
        }
        return tokens;
    }

    private List<String> buildGearTokens(String motoreNorm, String tipoCambioUtente) {
        List<String> tokens = new ArrayList<>(matchTokens(motoreNorm, GEAR_TOKENS));
        if (tipoCambioUtente != null && !tipoCambioUtente.isBlank()) {
            String t = normalize(tipoCambioUtente);
            if (!tokens.contains(t)) tokens.add(0, t);
        }
        return tokens;
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
        return m.find() ? m.group(1).replace(",", ".") : null;
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

    private record LinkEntry(String name, String url, int cv, String cilindrata) {}

    private record GenerazioneResult(String url, int annoEffettivo, boolean isFallback) {}
}
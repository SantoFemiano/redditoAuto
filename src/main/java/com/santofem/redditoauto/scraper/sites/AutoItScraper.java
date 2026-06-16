package com.santofem.redditoauto.scraper.sites;

import com.santofem.redditoauto.scraper.MultiSiteScraperResult;
import com.santofem.redditoauto.scraper.UrlScraperStrategy;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scraper specializzato per Auto.it.
 *
 * Auto.it è un portale automotive italiano con:
 * - Schede tecniche ufficiali dei costruttori
 * - Prezzi di listino per il mercato italiano
 * - Dati consumi e emissioni CO2
 *
 * URL tipici:
 *   https://www.auto.it/modelli/audi/a1/
 *   https://www.auto.it/listino/volkswagen/golf/
 *   https://www.auto.it/scheda-tecnica/bmw/serie-3/
 */
@Component
@Slf4j
public class AutoItScraper implements UrlScraperStrategy {

    private static final String SITE_NAME = "auto.it";

    private static final Pattern PREZZO_PAT = Pattern.compile(
            "(?:€|euro)?\\s*(\\d{2,3}[.,]\\d{3})(?:[.,]\\d{2})?\\s*(?:€|euro)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ANNO_PAT = Pattern.compile("\\b(20[0-2]\\d)\\b");

    // JSON-LD patterns (auto.it usa schema.org Car)
    private static final Pattern JSONLD_PRICE  = Pattern.compile("\"price\"\\s*:\\s*\"?([\\d.,]+)\"?");
    private static final Pattern JSONLD_NAME   = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JSONLD_BRAND  = Pattern.compile("\"brand\"\\s*:\\s*\\{[^}]*\"name\"\\s*:\\s*\"([^\"]+)\"");

    @Value("${scraper.timeout-ms:12000}")
    private int timeoutMs;

    @Value("${scraper.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36}")
    private String userAgent;

    @Value("${scraper.max-text-length:4000}")
    private int maxTextLength;

    @Override
    public boolean supports(String url) {
        return url != null && (url.contains("auto.it") && !url.contains("autoscout24")
                && !url.contains("automoto") && !url.contains("subito"));
    }

    @Override
    public String siteName() {
        return SITE_NAME;
    }

    @Override
    public MultiSiteScraperResult scrape(String url) {
        log.info("[AutoIt] Scraping: {}", url);
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .ignoreHttpErrors(true)
                    .followRedirects(true)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
                    .referrer("https://www.auto.it/")
                    .get();

            doc.select("nav, header, footer, script, style, iframe, noscript, .ads, .cookie, .newsletter, .social").remove();

            // Tenta prima JSON-LD (più affidabile per auto.it che usa schema.org)
            Double prezzoJsonLd = estraiPrezzoJsonLd(doc);
            String nomeJsonLd   = estraiNomeJsonLd(doc);
            String brandJsonLd  = estraiBrandJsonLd(doc);

            String testo    = estraiTestoTecnico(doc, url, nomeJsonLd);
            Double prezzo   = prezzoJsonLd != null ? prezzoJsonLd : estraiPrezzoDom(doc);
            String marca    = brandJsonLd  != null ? brandJsonLd  : estraiMarcaDaUrl(url);
            String modello  = estraiModelloDaTitolo(doc, nomeJsonLd);
            int    anno     = estraiAnno(doc, url);

            log.info("[AutoIt] Estratto: {} chars, prezzo={} EUR, marca={}, modello={}, anno={}",
                    testo.length(), prezzo, marca, modello, anno);

            if (testo.isBlank() || testo.length() < 100) {
                log.warn("[AutoIt] Testo insufficiente per: {}", url);
                return MultiSiteScraperResult.empty(SITE_NAME, url);
            }

            return MultiSiteScraperResult.builder()
                    .testo(truncate(testo, maxTextLength))
                    .prezzoEur(prezzo)
                    .tipoPrezzo(prezzo != null ? MultiSiteScraperResult.TipoPrezzo.LISTINO : null)
                    .marcaHint(marca)
                    .modelloHint(modello)
                    .annoHint(anno)
                    .siteNome(SITE_NAME)
                    .url(url)
                    .build();

        } catch (IOException e) {
            log.warn("[AutoIt] Errore HTTP: {}", e.getMessage());
            return MultiSiteScraperResult.empty(SITE_NAME, url);
        }
    }

    // ── JSON-LD ──────────────────────────────────────────────────────────────

    private Double estraiPrezzoJsonLd(Document doc) {
        for (Element script : doc.select("script[type='application/ld+json']")) {
            String json = script.html();
            if (!json.contains("price") && !json.contains("Price")) continue;
            Matcher m = JSONLD_PRICE.matcher(json);
            if (m.find()) {
                try {
                    double v = Double.parseDouble(m.group(1).replace(".", "").replace(",", "."));
                    if (v >= 5000 && v <= 500000) return v;
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private String estraiNomeJsonLd(Document doc) {
        for (Element script : doc.select("script[type='application/ld+json']")) {
            String json = script.html();
            if (!json.contains("Car") && !json.contains("Vehicle")) continue;
            Matcher m = JSONLD_NAME.matcher(json);
            if (m.find()) return m.group(1).trim();
        }
        return null;
    }

    private String estraiBrandJsonLd(Document doc) {
        for (Element script : doc.select("script[type='application/ld+json']")) {
            String json = script.html();
            if (!json.contains("brand")) continue;
            Matcher m = JSONLD_BRAND.matcher(json);
            if (m.find()) return m.group(1).trim();
        }
        return null;
    }

    // ── Estrazione testo ─────────────────────────────────────────────────────

    private String estraiTestoTecnico(Document doc, String url, String nomeJsonLd) {
        StringBuilder sb = new StringBuilder("[FONTE: ").append(url).append("]\n");
        if (nomeJsonLd != null) sb.append("Veicolo: ").append(nomeJsonLd).append("\n\n");

        String[] selectors = {
                ".scheda-tecnica",
                ".technical-specs",
                ".car-specs",
                ".vehicle-details",
                "[class*='scheda']",
                "[class*='spec']",
                "[class*='tecnic']",
                "table",
                "dl",
                "article",
                "main",
                ".content"
        };

        for (String sel : selectors) {
            Elements els = doc.select(sel);
            if (!els.isEmpty()) {
                String t = els.text().trim();
                if (t.length() >= 200) {
                    sb.append(t);
                    return sb.toString();
                }
            }
        }

        if (doc.body() != null) sb.append(doc.body().text().trim());
        return sb.toString();
    }

    private Double estraiPrezzoDom(Document doc) {
        String[] priceSelectors = {
                ".prezzo", ".prezzo-listino", ".price",
                "[class*='prezzo']", "[class*='price']",
                ".listino", "[class*='listino']"
        };
        for (String sel : priceSelectors) {
            Element el = doc.selectFirst(sel);
            if (el != null) {
                Double p = parsePrezzo(el.text());
                if (p != null) return p;
            }
        }
        if (doc.body() != null) return parsePrezzo(doc.body().text());
        return null;
    }

    private Double parsePrezzo(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = PREZZO_PAT.matcher(text);
        while (m.find()) {
            try {
                String raw = m.group(1).replace(".", "").replace(",", "");
                double v = Double.parseDouble(raw);
                if (v >= 5000 && v <= 500000) return v;
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private String estraiMarcaDaUrl(String url) {
        String[] parts = url.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ((parts[i].equals("modelli") || parts[i].equals("listino")
                    || parts[i].equals("scheda-tecnica") || parts[i].equals("auto"))
                    && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        return null;
    }

    private String estraiModelloDaTitolo(Document doc, String nomeJsonLd) {
        if (nomeJsonLd != null) {
            String[] parti = nomeJsonLd.split("\\s+");
            if (parti.length > 1) return parti[1];
        }
        String title = doc.title();
        if (title != null) {
            String[] parti = title.split("\\s+");
            if (parti.length > 1) return parti[1];
        }
        return null;
    }

    private int estraiAnno(Document doc, String url) {
        Matcher urlMatcher = ANNO_PAT.matcher(url);
        if (urlMatcher.find()) return Integer.parseInt(urlMatcher.group(1));

        if (doc.body() != null) {
            Matcher m = ANNO_PAT.matcher(doc.body().text());
            while (m.find()) {
                int y = Integer.parseInt(m.group(1));
                if (y >= 2000 && y <= 2030) return y;
            }
        }
        return 0;
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "...[TRONCATO]";
    }
}

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
 * Scraper per siti di schede tecniche italiani:
 * - motorist.it
 * - auto.it
 * - autoblog.it
 * - quattroruote.it
 *
 * Questi siti espongono schede tecniche complete con prezzo di listino ufficiale.
 *
 * URL tipici:
 *   https://www.motorist.it/auto/volkswagen/golf/2022-7.5-iq-drive-1-5-tsi
 *   https://www.auto.it/volkswagen/golf/2022/scheda-tecnica
 *   https://www.quattroruote.it/listino/volkswagen/golf/2022
 */
@Component
@Slf4j
public class MotoristItalyScraper implements UrlScraperStrategy {

    private static final String SITE_NAME = "motorist/auto.it";
    private static final Pattern YEAR_PAT  = Pattern.compile("(20|19)\\d{2}");
    private static final Pattern PRICE_PAT = Pattern.compile(
            "[€]\\s*(\\d[\\d.]{2,8})"
            + "|(\\d[\\d.]{4,8})\\s*[€]",
            Pattern.CASE_INSENSITIVE
    );

    @Value("${scraper.timeout-ms:10000}")
    private int timeoutMs;

    @Value("${scraper.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36}")
    private String userAgent;

    @Value("${scraper.max-text-length:4000}")
    private int maxTextLength;

    @Override
    public boolean supports(String url) {
        if (url == null) return false;
        return url.contains("motorist.it")
                || url.contains("auto.it")
                || url.contains("autoblog.it")
                || url.contains("quattroruote.it");
    }

    @Override
    public String siteName() {
        return SITE_NAME;
    }

    @Override
    public MultiSiteScraperResult scrape(String url) {
        log.info("[MotoristItaly] Scraping: {}", url);
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .ignoreHttpErrors(true)
                    .followRedirects(true)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
                    .get();

            doc.select("nav, header, footer, script, style, iframe, .ads, .cookie-banner").remove();

            String testo    = estraiTestoTecnico(doc, url);
            Double prezzo   = estraiPrezzo(doc);
            String marca    = estraiMeta(doc, "og:brand", "make");
            String modello  = estraiMeta(doc, "og:model", "model");
            int    anno     = estraiAnno(doc, url);

            log.info("[MotoristItaly] Estratti: {} chars, prezzo={} EUR", testo.length(), prezzo);

            if (testo.isBlank() || testo.length() < 150) {
                log.warn("[MotoristItaly] Testo insufficiente");
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
            log.warn("[MotoristItaly] Errore HTTP: {}", e.getMessage());
            return MultiSiteScraperResult.empty(SITE_NAME, url);
        }
    }

    private String estraiTestoTecnico(Document doc, String url) {
        StringBuilder sb = new StringBuilder();
        sb.append("[FONTE: ").append(url).append("]\n");
        sb.append(doc.title()).append("\n\n");

        String[] selectors = {
                ".scheda-tecnica", ".technical-specs", ".specs-table",
                ".car-specs", ".dati-tecnici", "#scheda-tecnica",
                ".spec-list", ".caratteristiche",
                "table", "dl",
                "article", "main", ".content"
        };

        for (String sel : selectors) {
            Elements els = doc.select(sel);
            if (!els.isEmpty()) {
                String t = els.text().trim();
                if (t.length() >= 150) {
                    sb.append(t);
                    return sb.toString();
                }
            }
        }

        if (doc.body() != null) sb.append(doc.body().text().trim());
        return sb.toString();
    }

    private Double estraiPrezzo(Document doc) {
        // Cerca prezzo di listino nei selettori tipici di questi siti
        String[] selectors = {
                ".prezzo-listino", ".listino", ".price",
                "[class*='prezzo']", "[class*='price']",
                ".costo", ".valore"
        };
        for (String sel : selectors) {
            Element el = doc.selectFirst(sel);
            if (el == null) continue;
            Double price = parsePrezzo(el.text());
            if (price != null) return price;
        }
        return null;
    }

    private Double parsePrezzo(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = PRICE_PAT.matcher(text);
        while (m.find()) {
            for (int i = 1; i <= m.groupCount(); i++) {
                String g = m.group(i);
                if (g != null) {
                    try {
                        double v = Double.parseDouble(g.replace(".", ""));
                        if (v >= 5000 && v <= 500000) return v;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return null;
    }

    private String estraiMeta(Document doc, String... attrs) {
        for (String attr : attrs) {
            Element el = doc.selectFirst("meta[property='" + attr + "']");
            if (el == null) el = doc.selectFirst("meta[name='" + attr + "']");
            if (el != null) {
                String val = el.attr("content").trim();
                if (!val.isBlank()) return val;
            }
        }
        return null;
    }

    private int estraiAnno(Document doc, String url) {
        // Cerca nell'URL prima
        Matcher mu = YEAR_PAT.matcher(url);
        if (mu.find()) {
            int y = Integer.parseInt(mu.group());
            if (y >= 1990 && y <= 2030) return y;
        }
        // Poi nel body
        String text = doc.body() != null ? doc.body().text() : "";
        Matcher m = YEAR_PAT.matcher(text);
        while (m.find()) {
            int y = Integer.parseInt(m.group());
            if (y >= 1990 && y <= 2030) return y;
        }
        return 0;
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "...[TRONCATO]";
    }
}

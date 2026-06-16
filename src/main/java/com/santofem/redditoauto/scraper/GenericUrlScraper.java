package com.santofem.redditoauto.scraper;

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
 * Scraper generico Jsoup per siti non riconosciuti dal dispatcher.
 *
 * Estrae il testo più rilevante dalla pagina usando selettori semantici
 * (tabelle, article, main) e tenta di rilevare un prezzo nella pagina.
 */
@Component
@Slf4j
public class GenericUrlScraper implements UrlScraperStrategy {

    private static final String SITE_NAME = "generico";

    private static final Pattern PRICE_PAT = Pattern.compile(
            "[€$£]\\s*(\\d[\\d.,]{2,9})"
            + "|(\\d[\\d.,]{2,9})\\s*[€]"
            + "|(\\d[\\d.,]{2,9})\\s*euro",
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
        return true; // fallback universale
    }

    @Override
    public String siteName() {
        return SITE_NAME;
    }

    @Override
    public MultiSiteScraperResult scrape(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .ignoreHttpErrors(true)
                    .followRedirects(true)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
                    .get();

            doc.select("nav, header, footer, script, style, iframe, noscript, .ads, .cookie").remove();

            String testo = extractBestText(doc);
            Double prezzo = extractPrezzo(doc);

            if (testo.isBlank()) {
                log.warn("[GenericScraper] Testo vuoto per URL: {}", url);
                return MultiSiteScraperResult.empty(SITE_NAME, url);
            }

            String testoConFonte = "[FONTE: " + url + "]\n" + truncate(testo, maxTextLength);

            return MultiSiteScraperResult.builder()
                    .testo(testoConFonte)
                    .prezzoEur(prezzo)
                    .tipoPrezzo(prezzo != null ? MultiSiteScraperResult.TipoPrezzo.SCONOSCIUTO : null)
                    .siteNome(SITE_NAME)
                    .url(url)
                    .build();

        } catch (IOException e) {
            log.warn("[GenericScraper] Errore HTTP per {}: {}", url, e.getMessage());
            return MultiSiteScraperResult.empty(SITE_NAME, url);
        }
    }

    private String extractBestText(Document doc) {
        String[] selectors = {
                "table", ".technical-specs", ".specs-table", ".car-specs",
                "article", "main", ".content", "#content", "body"
        };
        for (String sel : selectors) {
            Elements els = doc.select(sel);
            if (!els.isEmpty()) {
                String t = els.text().trim();
                if (t.length() >= 200) return t;
            }
        }
        return doc.body() != null ? doc.body().text().trim() : "";
    }

    private Double extractPrezzo(Document doc) {
        String[] selectors = {
                ".prezzo", ".price", ".price-value", "[class*=price]",
                "[class*=prezzo]", ".offer-price", ".listing-price"
        };
        for (String sel : selectors) {
            Element el = doc.selectFirst(sel);
            if (el != null) {
                Double price = parsePrezzo(el.text());
                if (price != null) return price;
            }
        }
        // fallback: cerca nel body
        String bodyText = doc.body() != null ? doc.body().text() : "";
        return parsePrezzo(bodyText);
    }

    Double parsePrezzo(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = PRICE_PAT.matcher(text);
        while (m.find()) {
            for (int i = 1; i <= m.groupCount(); i++) {
                String g = m.group(i);
                if (g != null) {
                    try {
                        String cleaned = g.replace(".", "").replace(",", ".");
                        double v = Double.parseDouble(cleaned);
                        if (v >= 1000 && v <= 500000) return v;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return null;
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "...[TRONCATO]";
    }
}

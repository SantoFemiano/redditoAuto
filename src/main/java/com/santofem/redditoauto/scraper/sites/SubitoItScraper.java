package com.santofem.redditoauto.scraper.sites;

import com.santofem.redditoauto.scraper.MultiSiteScraperResult;
import com.santofem.redditoauto.scraper.UrlScraperStrategy;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scraper per subito.it (annunci auto usate).
 *
 * Estrae:
 * - Prezzo dell'annuncio (prezzo mercato usato)
 * - Anno immatricolazione
 * - Dati tecnici (km, carburante, cambio, potenza)
 * - Marca e modello dall'annuncio
 *
 * URL tipici:
 *   https://www.subito.it/auto-e-moto/auto/usate/volkswagen-golf-12345678.htm
 *
 * NOTA: subito.it usa rendering SSR + hydration React.
 * I dati principali sono presenti nell'HTML iniziale.
 */
@Component
@Slf4j
public class SubitoItScraper implements UrlScraperStrategy {

    private static final String SITE_NAME = "subito.it";
    private static final Pattern PRICE_PAT = Pattern.compile("(\\d[\\d.]{2,8})\\s*[€]?");
    private static final Pattern YEAR_PAT  = Pattern.compile("(20|19)\\d{2}");
    private static final Pattern JSONLD_PRICE = Pattern.compile("\"price\"\\s*:\\s*\"?([\\d.,]+)\"?");

    @Value("${scraper.timeout-ms:10000}")
    private int timeoutMs;

    @Value("${scraper.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36}")
    private String userAgent;

    @Value("${scraper.max-text-length:3000}")
    private int maxTextLength;

    @Override
    public boolean supports(String url) {
        return url != null && url.contains("subito.it");
    }

    @Override
    public String siteName() {
        return SITE_NAME;
    }

    @Override
    public MultiSiteScraperResult scrape(String url) {
        log.info("[SubitoIt] Scraping annuncio: {}", url);
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .ignoreHttpErrors(true)
                    .followRedirects(true)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
                    .referrer("https://www.subito.it/")
                    .get();

            // Prezzo da JSON-LD (più affidabile)
            Double prezzo = estraiPrezzoJsonLd(doc);
            if (prezzo == null) prezzo = estraiPrezzoDom(doc);

            doc.select("nav, header, footer, script, style, iframe, .cookie, .ads").remove();

            String testo   = estraiTestoAnnuncio(doc, url);
            String marca   = estraiMarca(doc);
            String modello = estraiModello(doc);
            int    anno    = estraiAnno(doc, url);

            log.info("[SubitoIt] Estratti: {} chars, prezzo={} EUR, anno={}", testo.length(), prezzo, anno);

            if (testo.isBlank() || testo.length() < 80) {
                log.warn("[SubitoIt] Testo annuncio insufficiente");
                return MultiSiteScraperResult.empty(SITE_NAME, url);
            }

            return MultiSiteScraperResult.builder()
                    .testo(truncate(testo, maxTextLength))
                    .prezzoEur(prezzo)
                    .tipoPrezzo(prezzo != null ? MultiSiteScraperResult.TipoPrezzo.USATO_MERCATO : null)
                    .marcaHint(marca)
                    .modelloHint(modello)
                    .annoHint(anno)
                    .siteNome(SITE_NAME)
                    .url(url)
                    .build();

        } catch (IOException e) {
            log.warn("[SubitoIt] Errore HTTP: {}", e.getMessage());
            return MultiSiteScraperResult.empty(SITE_NAME, url);
        }
    }

    private Double estraiPrezzoJsonLd(Document doc) {
        for (Element script : doc.select("script[type='application/ld+json']")) {
            String json = script.html();
            if (!json.contains("price")) continue;
            Matcher m = JSONLD_PRICE.matcher(json);
            if (m.find()) {
                try {
                    double v = Double.parseDouble(m.group(1).replace(".", "").replace(",", "."));
                    if (v >= 500 && v <= 200000) return v;
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private Double estraiPrezzoDom(Document doc) {
        String[] selectors = {
                "[class*='price']", "[class*='prezzo']",
                ".AdPrice_price", ".price", "h3.price"
        };
        for (String sel : selectors) {
            Element el = doc.selectFirst(sel);
            if (el == null) continue;
            String text = el.text().replaceAll("[^\\d,.]", " ");
            Matcher m = PRICE_PAT.matcher(text);
            if (m.find()) {
                try {
                    double v = Double.parseDouble(m.group(1).replace(".", ""));
                    if (v >= 500 && v <= 200000) return v;
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private String estraiTestoAnnuncio(Document doc, String url) {
        StringBuilder sb = new StringBuilder();
        sb.append("[FONTE: ").append(url).append("]\n");
        sb.append(doc.title()).append("\n\n");

        // Dettagli tecnici annuncio subito.it
        String[] selectors = {
                "[class*='AdDetails']", "[class*='ad-details']",
                "[class*='FeatureList']", "[class*='feature-list']",
                "[class*='Characteristics']",
                "[class*='description']", ".description",
                "dl", "table",
                "article", ".content"
        };
        for (String sel : selectors) {
            var els = doc.select(sel);
            if (!els.isEmpty()) {
                String t = els.text().trim();
                if (t.length() >= 80) {
                    sb.append(t);
                    return sb.toString();
                }
            }
        }
        if (doc.body() != null) sb.append(doc.body().text().trim());
        return sb.toString();
    }

    private String estraiMarca(Document doc) {
        Element el = doc.selectFirst("[data-make], [itemprop='brand'], .make");
        if (el != null) return el.text().trim();
        // Cerca nell'h1
        Element h1 = doc.selectFirst("h1");
        if (h1 != null) {
            String[] parole = h1.text().split("\\s+");
            if (parole.length > 0) return parole[0];
        }
        return null;
    }

    private String estraiModello(Document doc) {
        Element el = doc.selectFirst("[data-model], [itemprop='model'], .model");
        if (el != null) return el.text().trim();
        Element h1 = doc.selectFirst("h1");
        if (h1 != null) {
            String[] parole = h1.text().split("\\s+");
            if (parole.length > 1) return parole[1];
        }
        return null;
    }

    private int estraiAnno(Document doc, String url) {
        Matcher mu = YEAR_PAT.matcher(url);
        if (mu.find()) {
            int y = Integer.parseInt(mu.group());
            if (y >= 1990 && y <= 2030) return y;
        }
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

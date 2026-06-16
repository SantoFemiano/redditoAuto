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
 * Scraper specializzato per Infomotori.com.
 *
 * Infomotori è un portale automotive con:
 * - Schede tecniche complete
 * - Prezzi di listino aggiornati per il mercato italiano
 * - Configuratore e confronto modelli
 *
 * URL tipici:
 *   https://www.infomotori.com/auto/audi/a1/scheda-tecnica/
 *   https://www.infomotori.com/auto/volkswagen/golf/8-generazione/
 */
@Component
@Slf4j
public class InfomotoriScraper implements UrlScraperStrategy {

    private static final String SITE_NAME = "infomotori.com";

    private static final Pattern PREZZO_PAT = Pattern.compile(
            "(?:€|euro)?\\s*(\\d{2,3}[.,]\\d{3})(?:[.,]\\d{2})?\\s*(?:€|euro)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ANNO_PAT = Pattern.compile("\\b(20[0-2]\\d)\\b");

    @Value("${scraper.timeout-ms:12000}")
    private int timeoutMs;

    @Value("${scraper.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36}")
    private String userAgent;

    @Value("${scraper.max-text-length:4000}")
    private int maxTextLength;

    @Override
    public boolean supports(String url) {
        return url != null && url.contains("infomotori.com");
    }

    @Override
    public String siteName() {
        return SITE_NAME;
    }

    @Override
    public MultiSiteScraperResult scrape(String url) {
        log.info("[Infomotori] Scraping: {}", url);
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .ignoreHttpErrors(true)
                    .followRedirects(true)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
                    .referrer("https://www.infomotori.com/")
                    .get();

            doc.select("nav, header, footer, script, style, iframe, noscript, .ads, .cookie, .newsletter, .social, .sidebar").remove();

            String testo = estraiTestoTecnico(doc, url);
            Double prezzo = estraiPrezzo(doc);
            String[] hints = estraiHints(doc, url);
            int anno = estraiAnno(doc, url);

            log.info("[Infomotori] Estratto: {} chars, prezzo={} EUR, marca={}, modello={}, anno={}",
                    testo.length(), prezzo, hints[0], hints[1], anno);

            if (testo.isBlank() || testo.length() < 100) {
                log.warn("[Infomotori] Testo insufficiente per: {}", url);
                return MultiSiteScraperResult.empty(SITE_NAME, url);
            }

            return MultiSiteScraperResult.builder()
                    .testo(truncate(testo, maxTextLength))
                    .prezzoEur(prezzo)
                    .tipoPrezzo(prezzo != null ? MultiSiteScraperResult.TipoPrezzo.LISTINO : null)
                    .marcaHint(hints[0])
                    .modelloHint(hints[1])
                    .annoHint(anno)
                    .siteNome(SITE_NAME)
                    .url(url)
                    .build();

        } catch (IOException e) {
            log.warn("[Infomotori] Errore HTTP: {}", e.getMessage());
            return MultiSiteScraperResult.empty(SITE_NAME, url);
        }
    }

    private String estraiTestoTecnico(Document doc, String url) {
        StringBuilder sb = new StringBuilder("[FONTE: ").append(url).append("]\n");

        String title = doc.title();
        if (title != null && !title.isBlank()) {
            sb.append("Veicolo: ").append(title
                    .replace(" - Infomotori", "")
                    .replace(" | Infomotori", "")
                    .trim()).append("\n\n");
        }

        String[] selectors = {
                ".scheda-tecnica",
                ".specifiche-tecniche",
                ".car-details",
                ".vehicle-specs",
                "[class*='scheda']",
                "[class*='spec']",
                "[class*='tecnic']",
                "table.dati",
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

    private Double estraiPrezzo(Document doc) {
        String[] priceSelectors = {
                ".prezzo-listino",
                ".price",
                ".prezzo",
                "[class*='prezzo']",
                "[class*='price']",
                "[class*='listino']",
                ".costo"
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

    private String[] estraiHints(Document doc, String url) {
        String marca = null;
        String modello = null;

        String title = doc.title();
        if (title != null && !title.isBlank()) {
            String clean = title
                    .replace(" - Infomotori", "")
                    .replace(" | Infomotori", "")
                    .trim();
            String[] parti = clean.split("\\s+");
            if (parti.length > 0) marca = parti[0];
            if (parti.length > 1) modello = parti[1];
        }

        // Da URL: /auto/audi/a1/
        if (marca == null || modello == null) {
            String[] urlParts = url.split("/");
            for (int i = 0; i < urlParts.length - 1; i++) {
                if (urlParts[i].equals("auto") && i + 2 < urlParts.length) {
                    if (marca == null) marca = urlParts[i + 1];
                    if (modello == null) modello = urlParts[i + 2];
                    break;
                }
            }
        }

        return new String[]{ marca, modello };
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

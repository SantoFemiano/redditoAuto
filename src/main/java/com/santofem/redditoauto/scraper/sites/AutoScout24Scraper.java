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
 * Scraper specializzato per AutoScout24 (autoscout24.it / autoscout24.com).
 *
 * AutoScout24 è una piattaforma di compravendita auto che espone:
 * - Prezzo di vendita (usato/km0)
 * - Anno immatricolazione
 * - Dati tecnici (potenza, carburante, cambio, km)
 *
 * URL tipici:
 *   https://www.autoscout24.it/annunci/volkswagen-golf-tdi-...-ID123456
 *   https://www.autoscout24.com/listings/volkswagen-golf-...
 *
 * AVVERTENZE:
 * AutoScout24 usa rendering React lato client. Jsoup recupera l'HTML del server-side
 * rendering (SSR) con i dati embedded in tag <script type="application/ld+json">.
 * Questa implementazione legge prima i JSON-LD e poi i tag HTML come fallback.
 */
@Component
@Slf4j
public class AutoScout24Scraper implements UrlScraperStrategy {

    private static final String SITE_NAME = "autoscout24.it";
    private static final Pattern PRICE_PAT = Pattern.compile("[€\\$]?\\s*(\\d[\\d.]{2,8})(?:[\\s,]*(\\d{2}))?\\s*[€]?");
    private static final Pattern YEAR_PAT  = Pattern.compile("(20|19)\\d{2}");
    // JSON-LD price pattern
    private static final Pattern JSONLD_PRICE = Pattern.compile("\"price\"\\s*:\\s*\"?([\\d.,]+)\"?");
    private static final Pattern JSONLD_NAME  = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    @Value("${scraper.timeout-ms:12000}")
    private int timeoutMs;

    @Value("${scraper.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36}")
    private String userAgent;

    @Value("${scraper.max-text-length:4000}")
    private int maxTextLength;

    @Override
    public boolean supports(String url) {
        return url != null && (url.contains("autoscout24.it") || url.contains("autoscout24.com"));
    }

    @Override
    public String siteName() {
        return SITE_NAME;
    }

    @Override
    public MultiSiteScraperResult scrape(String url) {
        log.info("[AutoScout24] Scraping: {}", url);
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .ignoreHttpErrors(true)
                    .followRedirects(true)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .referrer("https://www.autoscout24.it/")
                    .get();

            // 1. Tenta estrazione da JSON-LD (più affidabile)
            Double prezzoJsonLd = estraiPrezzoJsonLd(doc);
            String nomeJsonLd   = estraiNomeJsonLd(doc);

            // 2. Estrae testo visibile rilevante
            doc.select("nav, header, footer, script, style, iframe, .cookie-banner").remove();
            String testo = estraiTestoTecnico(doc, url, nomeJsonLd);

            // 3. Prezzo: JSON-LD oppure cerca nel DOM
            Double prezzo = prezzoJsonLd != null ? prezzoJsonLd : estraiPrezzoDom(doc);

            // 4. Hint identità
            String marcaHint   = estraiMarca(doc, nomeJsonLd);
            String modelloHint = estraiModello(doc, nomeJsonLd);
            int    annoHint    = estraiAnno(doc);

            log.info("[AutoScout24] Estratti: {} chars, prezzo={} EUR, marca={}, modello={}, anno={}",
                    testo.length(), prezzo, marcaHint, modelloHint, annoHint);

            if (testo.isBlank() || testo.length() < 100) {
                log.warn("[AutoScout24] Testo insufficiente");
                return MultiSiteScraperResult.empty(SITE_NAME, url);
            }

            return MultiSiteScraperResult.builder()
                    .testo(truncate(testo, maxTextLength))
                    .prezzoEur(prezzo)
                    .tipoPrezzo(prezzo != null ? MultiSiteScraperResult.TipoPrezzo.USATO_MERCATO : null)
                    .marcaHint(marcaHint)
                    .modelloHint(modelloHint)
                    .annoHint(annoHint)
                    .siteNome(SITE_NAME)
                    .url(url)
                    .build();

        } catch (IOException e) {
            log.warn("[AutoScout24] Errore HTTP: {}", e.getMessage());
            return MultiSiteScraperResult.empty(SITE_NAME, url);
        }
    }

    // ── Estrazione JSON-LD ────────────────────────────────────────────────────

    private Double estraiPrezzoJsonLd(Document doc) {
        for (Element script : doc.select("script[type='application/ld+json']")) {
            String json = script.html();
            if (!json.contains("price")) continue;
            Matcher m = JSONLD_PRICE.matcher(json);
            if (m.find()) {
                try {
                    double v = Double.parseDouble(m.group(1).replace(".", "").replace(",", "."));
                    if (v >= 500 && v <= 500000) return v;
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private String estraiNomeJsonLd(Document doc) {
        for (Element script : doc.select("script[type='application/ld+json']")) {
            String json = script.html();
            if (!json.contains("Vehicle") && !json.contains("Car")) continue;
            Matcher m = JSONLD_NAME.matcher(json);
            if (m.find()) return m.group(1).trim();
        }
        return null;
    }

    // ── Estrazione DOM ────────────────────────────────────────────────────────

    private String estraiTestoTecnico(Document doc, String url, String nomeJsonLd) {
        StringBuilder sb = new StringBuilder();
        sb.append("[FONTE: ").append(url).append("]\n");
        if (nomeJsonLd != null) sb.append("Veicolo: ").append(nomeJsonLd).append("\n\n");

        // Selettori specifici AutoScout24
        String[] selectors = {
                "[data-cy='listing-details']",
                "[data-testid='vehicle-details']",
                ".sc-vehicle-details",
                ".cldt-stage-primary-keyfacts",
                ".key-attributes",
                ".details-table",
                "[class*='detail']",
                "dl", "table",
                "article", "main"
        };

        for (String sel : selectors) {
            Elements els = doc.select(sel);
            if (!els.isEmpty()) {
                String t = els.text().trim();
                if (t.length() >= 100) {
                    sb.append(t).append("\n");
                    break;
                }
            }
        }

        if (sb.length() < 200) {
            sb.append(doc.body() != null ? doc.body().text().trim() : "");
        }
        return sb.toString();
    }

    private Double estraiPrezzoDom(Document doc) {
        String[] selectors = {
                "[data-cy='price-label']",
                "[data-testid='price']",
                ".cldt-price",
                ".sc-price",
                "[class*='price']",
                ".price-container"
        };
        for (String sel : selectors) {
            Element el = doc.selectFirst(sel);
            if (el == null) continue;
            Matcher m = PRICE_PAT.matcher(el.text());
            if (m.find()) {
                try {
                    String raw = m.group(1).replace(".", "");
                    if (m.group(2) != null) raw += "." + m.group(2);
                    double v = Double.parseDouble(raw);
                    if (v >= 500 && v <= 500000) return v;
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private String estraiMarca(Document doc, String nomeJsonLd) {
        if (nomeJsonLd != null && !nomeJsonLd.isBlank()) {
            String[] parti = nomeJsonLd.split("\\s+");
            if (parti.length > 0) return parti[0];
        }
        Element brand = doc.selectFirst("[data-make], [data-brand], .make-name");
        return brand != null ? brand.text().trim() : null;
    }

    private String estraiModello(Document doc, String nomeJsonLd) {
        if (nomeJsonLd != null && !nomeJsonLd.isBlank()) {
            String[] parti = nomeJsonLd.split("\\s+");
            if (parti.length > 1) return parti[1];
        }
        Element model = doc.selectFirst("[data-model], .model-name");
        return model != null ? model.text().trim() : null;
    }

    private int estraiAnno(Document doc) {
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

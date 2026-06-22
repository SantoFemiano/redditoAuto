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
 * Scraper specializzato per URL diretti di auto-data.net.
 *
 * Gestisce URL del tipo:
 *   https://www.auto-data.net/it/audi-a1-sportback-ii-1.4-tfsi-150hp-13498
 *
 * Estrae scheda tecnica strutturata dalle tabelle e inferisce
 * marca, modello e anno dal titolo della pagina.
 *
 * NOTA: non usa la navigazione a 5 livelli di AutoDataNetScraper;
 * riceve direttamente l'URL della motorizzazione.
 */
@Component
@Slf4j
public class AutoDataNetUrlScraper implements UrlScraperStrategy {

    private static final String SITE_NAME = "auto-data.net";
    private static final Pattern YEAR_PAT = Pattern.compile("(20|19)\\d{2}");
    private static final Pattern MONTH_YEAR_PAT = Pattern.compile("(?i)(?:gennaio|febbraio|marzo|aprile|maggio|giugno|luglio|agosto|settembre|ottobre|novembre|dicembre)\\s*,?\\s*((?:19|20)\\d{2})");

    @Value("${scraper.timeout-ms:10000}")
    private int timeoutMs;

    @Value("${scraper.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36}")
    private String userAgent;

    @Value("${scraper.max-text-length:4000}")
    private int maxTextLength;

    @Override
    public boolean supports(String url) {
        return url != null && url.contains("auto-data.net");
    }

    @Override
    public String siteName() {
        return SITE_NAME;
    }

    @Override
    public MultiSiteScraperResult scrape(String url) {
        log.info("[AutoDataNetUrl] Scraping URL diretto: {}", url);

        // 1. SCUDO DI PROTEZIONE: Verifichiamo che sia la scheda di una macchina
        String tipoUrl = identificaTipoUrl(url);
        if (!"VEHICLE".equals(tipoUrl)) {
            log.warn("[AutoDataNetUrl] URL scartato perché non punta a un veicolo finale. Rilevato: {}", tipoUrl);
            return MultiSiteScraperResult.empty(SITE_NAME, url);
        }

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .ignoreHttpErrors(true)
                    .followRedirects(true)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
                    .referrer("https://www.auto-data.net/it/")
                    .get();

            // Sostituito con il nuovo metodo chirurgico
            String testo = estraiTabelleTecniche(doc, url);

            if (testo.isBlank() || testo.length() < 200) {
                log.warn("[AutoDataNetUrl] Testo troppo corto o vuoto: {} chars", testo.length());
                return MultiSiteScraperResult.empty(SITE_NAME, url);
            }

            // Inferisce identità dal titolo pagina (es. "Audi A1 Sportback 2020 1.4 TFSI")
            String titolo = doc.title();
            String[] hint = parseIdentitaDaTitolo(titolo);

            log.info("[AutoDataNetUrl] Estratti {} chars da {}", testo.length(), SITE_NAME);

            // Se abbiamo trovato annoFrom/annoTo nella tabella, preferiscili
            int annoHint = 0;
            if (foundTo != null) annoHint = foundTo;
            else if (foundFrom != null) annoHint = foundFrom;

            if (!savedRows.isEmpty()) log.debug("[AutoDataNetUrl] Sample rows: {}", savedRows.subList(0, Math.min(8, savedRows.size())));

            return MultiSiteScraperResult.builder()
                    .testo(truncate(testo, maxTextLength))
                    .siteNome(SITE_NAME)
                    .url(url)
                    .marcaHint(hint[0])
                    .modelloHint(hint[1])
                    .annoHint(annoHint)
                    .build();

        } catch (IOException e) {
            log.warn("[AutoDataNetUrl] Errore HTTP: {}", e.getMessage());
            return MultiSiteScraperResult.empty(SITE_NAME, url);
        }
    }

    private String identificaTipoUrl(String url) {
        if (url == null) return "UNKNOWN";
        if (url.contains("-brand-")) return "BRAND";
        if (url.contains("-model-")) return "MODEL";
        if (url.contains("-generation-")) return "GENERATION";
        // Se non ha i suffissi di navigazione ma termina con un numero, è un veicolo
        if (url.matches(".*-\\d+/?$")) return "VEHICLE";
        return "UNKNOWN";
    }

    private String estraiTabelleTecniche(Document doc, String url) {
        // Pulizia aggressiva del DOM prima di leggere
        doc.select("nav, header, footer, script, style, iframe, .ad970, .ads, .cookie, noscript").remove();
        // Rimuove i link in fondo alla pagina (es. "Altre auto di questa generazione") che confondono Gemini
        doc.select(".breadcrumb, .similar-cars, a[href*=-model-], a[href*=-generation-]").remove();

        StringBuilder sb = new StringBuilder();
        sb.append("[FONTE: ").append(url).append("]\n");
        sb.append("TITOLO: ").append(doc.title()).append("\n\n");

        // Cerca la tabella specifica delle specifiche tecniche
        Elements tabelleTecniche = doc.select("table.cardetails, table.car-specs");
        if (tabelleTecniche.isEmpty()) {
            tabelleTecniche = doc.select("table"); // Fallback
        }

        Integer foundFrom = null;
        Integer foundTo = null;
        List<String> savedRows = new java.util.ArrayList<>();

        if (!tabelleTecniche.isEmpty()) {
            // Estrae SOLO dalla tabella principale per evitare contaminazioni
            Element tabellaPrincipale = tabelleTecniche.first();
            for (Element row : tabellaPrincipale.select("tr")) {
                Elements cells = row.select("td, th");
                if (cells.size() >= 2) {
                    String label = cells.get(0).text().trim();
                    String value = cells.get(1).text().trim();

                    // Proviamo a catturare Inizio/Fine anno di produzione
                    String labelNorm = label.toLowerCase();
                    String valNorm = value.replace('\u00A0', ' ').trim();
                    Matcher mMonth = MONTH_YEAR_PAT.matcher(valNorm);
                    if (labelNorm.contains("inizio") && labelNorm.contains("anno")) {
                        if (mMonth.find()) {
                            try { foundFrom = Integer.parseInt(mMonth.group(1));
                                log.debug("[AutoDataNetUrl] Parsed annoInizio (month) from '{}': {}", valNorm, foundFrom);
                            } catch (NumberFormatException ignored) { log.debug("[AutoDataNetUrl] Failed parsing month-year '{}'", valNorm); }
                        } else {
                            Matcher m = YEAR_PAT.matcher(valNorm);
                            if (m.find()) { try { foundFrom = Integer.parseInt(m.group()); log.debug("[AutoDataNetUrl] Parsed annoInizio from '{}': {}", valNorm, foundFrom); } catch (NumberFormatException ignored) {} }
                        }
                    }
                    if (labelNorm.contains("fine") && labelNorm.contains("anno")) {
                        if (mMonth.find()) {
                            try { foundTo = Integer.parseInt(mMonth.group(1));
                                log.debug("[AutoDataNetUrl] Parsed annoFine (month) from '{}': {}", valNorm, foundTo);
                            } catch (NumberFormatException ignored) { log.debug("[AutoDataNetUrl] Failed parsing month-year '{}'", valNorm); }
                        } else {
                            Matcher m = YEAR_PAT.matcher(valNorm);
                            if (m.find()) { try { foundTo = Integer.parseInt(m.group()); log.debug("[AutoDataNetUrl] Parsed annoFine from '{}': {}", valNorm, foundTo); } catch (NumberFormatException ignored) {} }
                        }
                    }

                    // Condizione stringente: se la label è lunghissima, è testo spazzatura, lo ignoriamo
                    if (!label.isEmpty() && !value.isEmpty() && label.length() < 120) {
                        String rowLine = label + ": " + value;
                        sb.append(rowLine).append("\n");
                        savedRows.add(rowLine);
                    }
                }
            }
        }

        return sb.toString().trim();
    }
    /**
     * Inferisce [marca, modello, anno] dal titolo della pagina.
     * Es. "Audi A3 Sportback 2020 1.4 TFSI 150hp | auto-data.net" → ["Audi", "A3 Sportback", "2020"]
     */
    private String[] parseIdentitaDaTitolo(String titolo) {
        if (titolo == null || titolo.isBlank()) return new String[3];
        String pulito = titolo.replaceAll("\\|.*", "").trim();

        String anno = null;
        Matcher m = YEAR_PAT.matcher(pulito);
        if (m.find()) anno = m.group();

        String[] parole = pulito.split("\\s+");
        String marca   = parole.length > 0 ? parole[0] : null;
        String modello = parole.length > 1 ? parole[1] : null;

        return new String[]{ marca, modello, anno };
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "...[TRONCATO]";
    }
}

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
 *
 * Propaga nel MultiSiteScraperResult due set distinti di anni:
 * - annoInizioModello / annoFineModello → dal breadcrumb (anni del MODELLO/generazione)
 * - annoHint                            → anno inizio motorizzazione (retrocompatibilità)
 * Gli anni della motorizzazione (from/to dalla tabella) sono usati internamente
 * per annoHint ma non propagati separatamente (vengono estratti dall'AI via testo).
 */
@Component
@Slf4j
public class AutoDataNetUrlScraper implements UrlScraperStrategy {

    private static final String SITE_NAME = "auto-data.net";
    private static final Pattern YEAR_PAT = Pattern.compile("(20|19)\\d{2}");
    private static final Pattern MONTH_YEAR_PAT = Pattern.compile(
            "(?i)(?:gennaio|febbraio|marzo|aprile|maggio|giugno|luglio|agosto|settembre|ottobre|novembre|dicembre)\\s*,?\\s*((?:19|20)\\d{2})");
    // Breadcrumb: "Juke II (2019 - 2023)" oppure "Juke II (2019 - present/oggi)"
    private static final Pattern MODELLO_RANGE_PAT = Pattern.compile(
            "\\(((?:19|20)\\d{2})\\s*[-\u2013]\\s*((?:19|20)\\d{2}|present|oggi|\\?)\\)");
    private static final Pattern MODELLO_SOLO_PAT = Pattern.compile("\\(((?:19|20)\\d{2})\\)");

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

    private static record SchedaInfo(
            String testo,
            Integer from,         // anno inizio MOTORIZZAZIONE (dalla tabella)
            Integer to,           // anno fine MOTORIZZAZIONE (dalla tabella)
            java.util.List<String> sampleRows
    ) {}

    @Override
    public MultiSiteScraperResult scrape(String url) {
        log.info("[AutoDataNetUrl] Scraping URL diretto: {}", url);

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

            // IMPORTANTE: parseAnniModelloDaBreadcrumb va chiamato PRIMA di estraiTabelleTecniche
            // perché quest'ultimo rimuove il breadcrumb dal DOM con doc.select(".breadcrumb,...").remove()
            int[] anniModello = parseAnniModelloDaBreadcrumb(doc);
            int modelloFrom = anniModello[0];
            int modelloTo   = anniModello[1];
            log.info("[AutoDataNetUrl] Anni modello da breadcrumb: inizio={} fine={}", modelloFrom, modelloTo);

            SchedaInfo scheda = estraiTabelleTecniche(doc, url);
            String testo = scheda == null ? "" : scheda.testo();

            if (testo.isBlank() || testo.length() < 200) {
                log.warn("[AutoDataNetUrl] Testo troppo corto o vuoto: {} chars", testo.length());
                return MultiSiteScraperResult.empty(SITE_NAME, url);
            }

            String titolo = doc.title();
            String[] hint = parseIdentitaDaTitolo(titolo);

            // Anni MOTORIZZAZIONE (dalla tabella scheda tecnica)
            int motorFrom = scheda != null && scheda.from() != null ? scheda.from() : 0;
            int motorTo   = scheda != null && scheda.to()   != null ? scheda.to()   : 0;

            // annoHint retrocompatibilità: preferisce inizio motorizzazione, fallback inizio modello
            int annoHint = motorFrom > 0 ? motorFrom : modelloFrom;

            log.info("[AutoDataNetUrl] Anni motorizzazione: inizio={} fine={} | Anni modello: inizio={} fine={}",
                    motorFrom, motorTo, modelloFrom, modelloTo);

            if (scheda != null && scheda.sampleRows() != null && !scheda.sampleRows().isEmpty()) {
                log.debug("[AutoDataNetUrl] Sample rows: {}",
                        scheda.sampleRows().subList(0, Math.min(8, scheda.sampleRows().size())));
            }

            return MultiSiteScraperResult.builder()
                    .testo(truncate(testo, maxTextLength))
                    .siteNome(SITE_NAME)
                    .url(url)
                    .marcaHint(hint[0])
                    .modelloHint(hint[1])
                    .annoHint(annoHint)
                    .annoInizioModello(modelloFrom)
                    .annoFineModello(modelloTo)
                    .build();

        } catch (IOException e) {
            log.warn("[AutoDataNetUrl] Errore HTTP: {}", e.getMessage());
            return MultiSiteScraperResult.empty(SITE_NAME, url);
        }
    }

    /**
     * Estrae anni inizio/fine del MODELLO dal breadcrumb della pagina.
     * Deve essere chiamato PRIMA di estraiTabelleTecniche che pulisce il DOM.
     *
     * auto-data.net ha struttura breadcrumb tipo:
     *   Nissan > Juke > Juke II (2019 - 2023)
     *   oppure: Juke II (2019 - present)
     *
     * @return int[2]: [annoInizio, annoFine] — 0 se non trovato
     */
    private int[] parseAnniModelloDaBreadcrumb(Document doc) {
        Elements candidati = doc.select(
                "#breadcrumb a, #breadcrumb span, .breadcrumb a, .breadcrumb span, nav a, nav span");

        for (Element el : candidati) {
            String txt = el.text();
            Matcher m = MODELLO_RANGE_PAT.matcher(txt);
            if (m.find()) {
                int from = Integer.parseInt(m.group(1));
                String toStr = m.group(2);
                int to = toStr.matches("\\d+") ? Integer.parseInt(toStr) : 0;
                log.debug("[AutoDataNetUrl] Breadcrumb modello range in '{}': {}-{}", txt, from, to);
                return new int[]{from, to};
            }
            Matcher m2 = MODELLO_SOLO_PAT.matcher(txt);
            if (m2.find()) {
                int from = Integer.parseInt(m2.group(1));
                log.debug("[AutoDataNetUrl] Breadcrumb modello solo anno in '{}': {}", txt, from);
                return new int[]{from, 0};
            }
        }

        // Fallback: h1/h2 che spesso contengono il nome generazione con anni
        for (Element heading : doc.select("h1, h2, .car-title, .model-name, .generation-name")) {
            String txt = heading.text();
            Matcher m = MODELLO_RANGE_PAT.matcher(txt);
            if (m.find()) {
                int from = Integer.parseInt(m.group(1));
                String toStr = m.group(2);
                int to = toStr.matches("\\d+") ? Integer.parseInt(toStr) : 0;
                log.debug("[AutoDataNetUrl] H1/H2 modello range in '{}': {}-{}", txt, from, to);
                return new int[]{from, to};
            }
        }

        log.debug("[AutoDataNetUrl] Nessun anno modello trovato nel breadcrumb");
        return new int[]{0, 0};
    }

    private String identificaTipoUrl(String url) {
        if (url == null) return "UNKNOWN";
        if (url.contains("-brand-")) return "BRAND";
        if (url.contains("-model-")) return "MODEL";
        if (url.contains("-generation-")) return "GENERATION";
        if (url.matches(".*-\\d+/?$")) return "VEHICLE";
        return "UNKNOWN";
    }

    private SchedaInfo estraiTabelleTecniche(Document doc, String url) {
        doc.select("nav, header, footer, script, style, iframe, .ad970, .ads, .cookie, noscript").remove();
        doc.select(".breadcrumb, .similar-cars, a[href*=-model-], a[href*=-generation-]").remove();

        StringBuilder sb = new StringBuilder();
        sb.append("[FONTE: ").append(url).append("]\n");
        sb.append("TITOLO: ").append(doc.title()).append("\n\n");

        Elements tabelleTecniche = doc.select("table.cardetails, table.car-specs");
        if (tabelleTecniche.isEmpty()) {
            tabelleTecniche = doc.select("table");
        }

        Integer foundFrom = null;
        Integer foundTo = null;
        java.util.List<String> savedRows = new java.util.ArrayList<>();

        if (!tabelleTecniche.isEmpty()) {
            Element tabellaPrincipale = tabelleTecniche.first();
            for (Element row : tabellaPrincipale.select("tr")) {
                Elements cells = row.select("td, th");
                if (cells.size() >= 2) {
                    String label = cells.get(0).text().trim();
                    String value = cells.get(1).text().trim();
                    String labelNorm = label.toLowerCase();
                    String valNorm = value.replace('\u00A0', ' ').trim();
                    Matcher mMonth = MONTH_YEAR_PAT.matcher(valNorm);
                    if (labelNorm.contains("inizio") && labelNorm.contains("anno")) {
                        if (mMonth.find()) {
                            try { foundFrom = Integer.parseInt(mMonth.group(1));
                                log.debug("[AutoDataNetUrl] annoInizio motorizzazione (month): {}", foundFrom);
                            } catch (NumberFormatException ignored) {}
                        } else {
                            Matcher m = YEAR_PAT.matcher(valNorm);
                            if (m.find()) { try { foundFrom = Integer.parseInt(m.group()); } catch (NumberFormatException ignored) {} }
                        }
                    }
                    if (labelNorm.contains("fine") && labelNorm.contains("anno")) {
                        if (mMonth.find()) {
                            try { foundTo = Integer.parseInt(mMonth.group(1));
                                log.debug("[AutoDataNetUrl] annoFine motorizzazione (month): {}", foundTo);
                            } catch (NumberFormatException ignored) {}
                        } else {
                            Matcher m = YEAR_PAT.matcher(valNorm);
                            if (m.find()) { try { foundTo = Integer.parseInt(m.group()); } catch (NumberFormatException ignored) {} }
                        }
                    }
                    if (!label.isEmpty() && !value.isEmpty() && label.length() < 120) {
                        String rowLine = label + ": " + value;
                        sb.append(rowLine).append("\n");
                        savedRows.add(rowLine);
                    }
                }
            }
        }

        if (foundFrom == null || foundTo == null) {
            if (foundFrom == null) foundFrom = findYearInDocByLabels(doc, new String[]{"inizio anno", "inizio"});
            if (foundTo == null)   foundTo   = findYearInDocByLabels(doc, new String[]{"fine anno", "fine"});
        }

        return new SchedaInfo(sb.toString().trim(), foundFrom, foundTo, savedRows);
    }

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

    private Integer findYearInDocByLabels(Document doc, String[] labels) {
        for (Element e : doc.getAllElements()) {
            String txt = e.text();
            if (txt == null || txt.isBlank()) continue;
            String lower = txt.toLowerCase();
            boolean matches = false;
            for (String lab : labels) if (lower.contains(lab)) { matches = true; break; }
            if (!matches) continue;
            Matcher mMonth = MONTH_YEAR_PAT.matcher(txt);
            if (mMonth.find()) { try { return Integer.parseInt(mMonth.group(1)); } catch (NumberFormatException ignored) {} }
            Matcher m = YEAR_PAT.matcher(txt);
            if (m.find()) { try { return Integer.parseInt(m.group()); } catch (NumberFormatException ignored) {} }
            Element next = e.nextElementSibling();
            if (next != null) {
                String ntxt = next.text();
                Matcher mm = MONTH_YEAR_PAT.matcher(ntxt);
                if (mm.find()) { try { return Integer.parseInt(mm.group(1)); } catch (NumberFormatException ignored) {} }
                Matcher m2 = YEAR_PAT.matcher(ntxt);
                if (m2.find()) { try { return Integer.parseInt(m2.group()); } catch (NumberFormatException ignored) {} }
            }
        }
        return null;
    }
}

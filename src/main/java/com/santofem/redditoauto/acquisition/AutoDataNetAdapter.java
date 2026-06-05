package com.santofem.redditoauto.acquisition;

import com.santofem.redditoauto.scraper.AutoDataNetScraper;
import com.santofem.redditoauto.scraper.ScraperResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapter per auto-data.net.
 * Delega il fetch/navigazione ad AutoDataNetScraper (che mantiene la logica Jsoup specifica),
 * quindi struttura il testo grezzo in SourceFieldCandidate per la pipeline.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoDataNetAdapter extends AbstractJsoupAdapter {

    private final AutoDataNetScraper scraper;

    @Override
    public String sourceName() { return "auto-data.net"; }

    @Override
    public boolean supports(CarLookupRequest request) {
        // Sempre disponibile per qualsiasi richiesta auto
        return request.marca() != null && request.modello() != null;
    }

    @Override
    public SourceExtractionResult extract(CarLookupRequest request) {
        try {
            ScraperResult scraperResult = scraper.scrapeConRisultato(
                request.marca(), request.modello(), request.motore(),
                request.anno(),
                request.potenzaCv() != null ? request.potenzaCv() : 0,
                request.tipoCarburante(),
                request.tipoCambio()
            );

            if (!scraperResult.hasText()) {
                return SourceExtractionResult.failure(
                    sourceName(), request.anno(), "Testo non trovato su auto-data.net");
            }

            String snippet = sanitize(scraperResult.testo());
            List<SourceFieldCandidate<?>> candidates = parseSnippet(snippet);

            log.info("[AutoDataNetAdapter] Estratti {} candidati da {} chars",
                candidates.size(), snippet.length());

            if (scraperResult.annoFallback()) {
                return SourceExtractionResult.successWithFallback(
                    sourceName(), candidates, snippet,
                    scraperResult.annoRichiesto(), scraperResult.annoEffettivo());
            }
            return SourceExtractionResult.success(
                sourceName(), candidates, snippet, scraperResult.annoEffettivo());

        } catch (Exception e) {
            log.error("[AutoDataNetAdapter] Errore inatteso: {}", e.getMessage(), e);
            return SourceExtractionResult.failure(
                sourceName(), request.anno(), "Errore: " + e.getMessage());
        }
    }

    // -----------------------------------------------
    // Parser candidati dal testo grezzo
    // -----------------------------------------------

    private List<SourceFieldCandidate<?>> parseSnippet(String snippet) {
        List<SourceFieldCandidate<?>> candidates = new ArrayList<>();

        extractDouble(snippet, Pattern.compile(
            "(\\d{2,4})\\s*(?:kW|KW)"), "potenzaKw", 0.9)
            .ifPresent(candidates::add);

        extractDouble(snippet, Pattern.compile(
            "(\\d{2,4})\\s*(?:CV|HP|hp|cv|ps|PS)"), "potenzaCv", 0.9)
            .ifPresent(candidates::add);

        extractDouble(snippet, Pattern.compile(
            "(\\d{3,5})\\s*(?:cc|CC|cm³|cm3)"), "cilindrataCC", 0.85)
            .ifPresent(candidates::add);

        // Consumo medio: cerca pattern tipo "5.2 l/100km" o "5,2 l/100"
        extractDouble(snippet, Pattern.compile(
            "(\\d{1,2}[,.]\\d{1,2})\\s*(?:l/100|l/100km|L/100)"), "consumoMedio", 0.8)
            .ifPresent(c -> candidates.add(
                new SourceFieldCandidate<>("consumoMedio", c.value(), c.confidence(), c.source(), c.evidence())));

        // Carburante
        extractCarburante(snippet).ifPresent(candidates::add);

        // Cambio
        extractCambio(snippet).ifPresent(candidates::add);

        // Pneumatici (pattern: 195/65R15 o 205/55 R16)
        extractTyre(snippet, "misuraPneumaticiAnt").ifPresent(candidates::add);

        return candidates;
    }

    private java.util.Optional<SourceFieldCandidate<?>> extractDouble(
            String text, Pattern pattern, String fieldName, double confidence) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            try {
                double val = Double.parseDouble(m.group(1).replace(',', '.'));
                return java.util.Optional.of(new SourceFieldCandidate<>(
                    fieldName, val, confidence, sourceName(), m.group(0)));
            } catch (NumberFormatException ignored) {}
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<SourceFieldCandidate<?>> extractCarburante(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("diesel") || lower.contains("gasolio") || lower.contains("tdi") || lower.contains("cdi"))
            return java.util.Optional.of(SourceFieldCandidate.of("tipoCarburante", "DIESEL", 0.9, sourceName()));
        if (lower.contains("benzina") || lower.contains("petrol") || lower.contains("tsi") || lower.contains("tfsi"))
            return java.util.Optional.of(SourceFieldCandidate.of("tipoCarburante", "BENZINA", 0.9, sourceName()));
        if (lower.contains("elettric") || lower.contains("electric") || lower.contains("bev"))
            return java.util.Optional.of(SourceFieldCandidate.of("tipoCarburante", "ELETTRICO", 0.95, sourceName()));
        if (lower.contains("plug-in") || lower.contains("phev"))
            return java.util.Optional.of(SourceFieldCandidate.of("tipoCarburante", "PLUG_IN_HYBRID", 0.9, sourceName()));
        if (lower.contains("ibrido") || lower.contains("hybrid"))
            return java.util.Optional.of(SourceFieldCandidate.of("tipoCarburante", "IBRIDO", 0.85, sourceName()));
        if (lower.contains("metano") || lower.contains("cng"))
            return java.util.Optional.of(SourceFieldCandidate.of("tipoCarburante", "METANO", 0.9, sourceName()));
        if (lower.contains("gpl") || lower.contains("lpg"))
            return java.util.Optional.of(SourceFieldCandidate.of("tipoCarburante", "GPL", 0.9, sourceName()));
        return java.util.Optional.empty();
    }

    private java.util.Optional<SourceFieldCandidate<?>> extractCambio(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("dsg") || lower.contains("dct") || lower.contains("s-tronic"))
            return java.util.Optional.of(SourceFieldCandidate.of("tipoCambio", "AUTOMATICO", 0.9, sourceName()));
        if (lower.contains("automatico") || lower.contains("automatic") || lower.contains("automat"))
            return java.util.Optional.of(SourceFieldCandidate.of("tipoCambio", "AUTOMATICO", 0.85, sourceName()));
        if (lower.contains("manuale") || lower.contains("manual"))
            return java.util.Optional.of(SourceFieldCandidate.of("tipoCambio", "MANUALE", 0.85, sourceName()));
        if (lower.contains("cvt"))
            return java.util.Optional.of(SourceFieldCandidate.of("tipoCambio", "CVT", 0.9, sourceName()));
        return java.util.Optional.empty();
    }

    private java.util.Optional<SourceFieldCandidate<?>> extractTyre(String text, String fieldName) {
        Matcher m = Pattern.compile("(\\d{3}/\\d{2}\\s*[Rr]\\d{2})").matcher(text);
        if (m.find()) {
            return java.util.Optional.of(new SourceFieldCandidate<>(
                fieldName, m.group(1), 0.8, sourceName(), m.group(0)));
        }
        return java.util.Optional.empty();
    }
}

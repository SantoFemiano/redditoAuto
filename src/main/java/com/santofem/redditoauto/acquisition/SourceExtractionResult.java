package com.santofem.redditoauto.acquisition;

import java.util.List;

/**
 * Risultato dell'estrazione di un singolo adapter.
 * Contiene i candidati per campo, eventuali warning e lo snippet grezzo
 * che può essere passato all'AI se necessario.
 */
public record SourceExtractionResult(
        String source,
        boolean success,
        List<SourceFieldCandidate<?>> candidates,
        List<String> warnings,
        String rawSnippet,
        int annoRichiesto,
        int annoEffettivo,
        boolean annoFallback
) {
    /** Factory per estrazione riuscita senza fallback anno. */
    public static SourceExtractionResult success(
            String source,
            List<SourceFieldCandidate<?>> candidates,
            String rawSnippet,
            int anno) {
        return new SourceExtractionResult(
            source, true, candidates, List.of(), rawSnippet, anno, anno, false);
    }

    /** Factory per estrazione riuscita con anno fallback. */
    public static SourceExtractionResult successWithFallback(
            String source,
            List<SourceFieldCandidate<?>> candidates,
            String rawSnippet,
            int annoRichiesto,
            int annoEffettivo) {
        return new SourceExtractionResult(
            source, true, candidates, List.of(), rawSnippet,
            annoRichiesto, annoEffettivo, true);
    }

    /** Factory per estrazione fallita. */
    public static SourceExtractionResult failure(String source, int anno, String reason) {
        return new SourceExtractionResult(
            source, false, List.of(), List.of(reason), null, anno, anno, false);
    }

    public boolean hasRawSnippet() {
        return rawSnippet != null && !rawSnippet.isBlank();
    }

    public String buildWarningAnno() {
        if (!annoFallback) return null;
        return "Dati non disponibili per il " + annoRichiesto
             + ". Mostro la versione " + annoEffettivo + ".";
    }
}

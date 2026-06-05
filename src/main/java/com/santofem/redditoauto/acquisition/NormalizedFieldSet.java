package com.santofem.redditoauto.acquisition;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Insieme di campi normalizzati con confidence aggregata.
 * Prodotto dal {@link FieldNormalizer}, consumato da {@link AiCompletionService}
 * per sapere quali campi mancano e da {@link FieldConfidenceService} per il report finale.
 */
public class NormalizedFieldSet {

    private final Map<String, SourceFieldCandidate<?>> fields = new HashMap<>();

    public void put(SourceFieldCandidate<?> candidate) {
        // Mantiene il candidato con confidence più alta se il campo è già presente
        fields.merge(candidate.fieldName(), candidate, (existing, incoming) ->
            incoming.confidence() > existing.confidence() ? incoming : existing);
    }

    public <T> Optional<T> get(String fieldName, Class<T> type) {
        SourceFieldCandidate<?> c = fields.get(fieldName);
        if (c == null || c.value() == null) return Optional.empty();
        if (type.isInstance(c.value())) return Optional.of(type.cast(c.value()));
        return Optional.empty();
    }

    public double confidenceFor(String fieldName) {
        SourceFieldCandidate<?> c = fields.get(fieldName);
        return c != null ? c.confidence() : 0.0;
    }

    public boolean isMissing(String fieldName) {
        SourceFieldCandidate<?> c = fields.get(fieldName);
        return c == null || c.value() == null || c.confidence() < 0.3;
    }

    public boolean isLowConfidence(String fieldName) {
        return confidenceFor(fieldName) < 0.6;
    }

    public Map<String, SourceFieldCandidate<?>> allFields() {
        return Map.copyOf(fields);
    }
}

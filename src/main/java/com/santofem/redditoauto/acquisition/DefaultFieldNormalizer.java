package com.santofem.redditoauto.acquisition;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Implementazione di default del normalizzatore.
 * Converte unità (kW↔CV), uniforma enum carburante/cambio,
 * normalizza misure pneumatici (es. "195/65 R15" → "195/65R15").
 */
@Slf4j
@Component
public class DefaultFieldNormalizer implements FieldNormalizer {

    private static final Map<String, String> CARBURANTE_MAP = Map.ofEntries(
        Map.entry("diesel", "DIESEL"),
        Map.entry("gasolio", "DIESEL"),
        Map.entry("benzina", "BENZINA"),
        Map.entry("petrol", "BENZINA"),
        Map.entry("gasoline", "BENZINA"),
        Map.entry("gpl", "GPL"),
        Map.entry("lpg", "GPL"),
        Map.entry("metano", "METANO"),
        Map.entry("cng", "METANO"),
        Map.entry("elettrico", "ELETTRICO"),
        Map.entry("electric", "ELETTRICO"),
        Map.entry("ibrido", "IBRIDO"),
        Map.entry("hybrid", "IBRIDO"),
        Map.entry("plug-in", "PLUG_IN_HYBRID"),
        Map.entry("phev", "PLUG_IN_HYBRID")
    );

    private static final Map<String, String> CAMBIO_MAP = Map.ofEntries(
        Map.entry("manuale", "MANUALE"),
        Map.entry("manual", "MANUALE"),
        Map.entry("automatico", "AUTOMATICO"),
        Map.entry("automatic", "AUTOMATICO"),
        Map.entry("dsg", "AUTOMATICO"),
        Map.entry("dct", "AUTOMATICO"),
        Map.entry("cvt", "CVT"),
        Map.entry("robotizzato", "ROBOTIZZATO"),
        Map.entry("edct", "AUTOMATICO")
    );

    @Override
    public NormalizedFieldSet normalize(List<SourceFieldCandidate<?>> candidates) {
        NormalizedFieldSet result = new NormalizedFieldSet();

        for (SourceFieldCandidate<?> c : candidates) {
            SourceFieldCandidate<?> normalized = switch (c.fieldName()) {
                case "potenzaKw" -> normalizeDouble(c, 10.0, 800.0);
                case "potenzaCv" -> normalizeDouble(c, 13.0, 1100.0);
                case "cilindrataCC" -> normalizeDouble(c, 600.0, 10000.0);
                case "consumoMedio", "consumoUrbano", "consumoExtraurbano" ->
                    normalizeDouble(c, 0.0, 40.0);
                case "tipoCarburante" -> normalizeEnum(c, CARBURANTE_MAP);
                case "tipoCambio" -> normalizeEnum(c, CAMBIO_MAP);
                case "misuraPneumaticiAnt", "misuraPneumaticiPost" -> normalizeTyre(c);
                default -> c;
            };
            if (normalized != null) result.put(normalized);
        }

        // Cross-derivazione kW↔CV se uno dei due manca
        crossDeriveKwCv(result);

        return result;
    }

    // -----------------------------------------------
    // helpers
    // -----------------------------------------------

    @SuppressWarnings("unchecked")
    private SourceFieldCandidate<?> normalizeDouble(SourceFieldCandidate<?> c, double min, double max) {
        if (c.value() == null) return null;
        try {
            double val = Double.parseDouble(c.value().toString().replace(',', '.'));
            if (val < min || val > max) {
                log.debug("[Normalizer] Campo '{}' fuori range [{}-{}]: {} → scartato",
                    c.fieldName(), min, max, val);
                return null;
            }
            return new SourceFieldCandidate<>(c.fieldName(), val, c.confidence(), c.source(), c.evidence());
        } catch (NumberFormatException e) {
            log.debug("[Normalizer] Campo '{}' non numerico: {}", c.fieldName(), c.value());
            return null;
        }
    }

    private SourceFieldCandidate<?> normalizeEnum(
            SourceFieldCandidate<?> c, Map<String, String> map) {
        if (c.value() == null) return null;
        String key = c.value().toString().toLowerCase().trim();
        String normalized = map.entrySet().stream()
            .filter(e -> key.contains(e.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(c.value().toString().toUpperCase());
        return new SourceFieldCandidate<>(c.fieldName(), normalized, c.confidence(), c.source(), c.evidence());
    }

    private SourceFieldCandidate<?> normalizeTyre(SourceFieldCandidate<?> c) {
        if (c.value() == null) return null;
        // Uniforma "195/65 R15" → "195/65R15"
        String normalized = c.value().toString().trim()
            .replaceAll("\\s+R", "R")
            .replaceAll("\\s+r", "R")
            .toUpperCase();
        return new SourceFieldCandidate<>(c.fieldName(), normalized, c.confidence(), c.source(), c.evidence());
    }

    private void crossDeriveKwCv(NormalizedFieldSet set) {
        if (set.isMissing("potenzaKw") && !set.isMissing("potenzaCv")) {
            double cv = (Double) set.get("potenzaCv", Double.class).orElse(0.0);
            double kw = cv * 0.7355;
            set.put(SourceFieldCandidate.of("potenzaKw", kw, 0.85, "derived:cv→kw"));
            log.debug("[Normalizer] potenzaKw derivata da CV: {} → {} kW", cv, kw);
        } else if (set.isMissing("potenzaCv") && !set.isMissing("potenzaKw")) {
            double kw = (Double) set.get("potenzaKw", Double.class).orElse(0.0);
            double cv = kw / 0.7355;
            set.put(SourceFieldCandidate.of("potenzaCv", cv, 0.85, "derived:kw→cv"));
            log.debug("[Normalizer] potenzaCv derivata da kW: {} → {} CV", kw, cv);
        }
    }
}

package com.santofem.redditoauto.acquisition;

/**
 * Rappresenta un singolo campo estratto da una fonte, con metadati di qualità.
 *
 * @param <T>        tipo del valore (Double, Integer, String, ecc.)
 * @param fieldName  nome canonico del campo (es. "potenzaKw", "consumoMedio")
 * @param value      valore estratto dalla fonte
 * @param confidence score [0.0, 1.0]: 0.0 = ignora, 1.0 = certezza assoluta
 * @param source     identificatore della fonte (es. "auto-data.net")
 * @param evidence   snippet grezzo da cui è stato estratto il valore
 */
public record SourceFieldCandidate<T>(
        String fieldName,
        T value,
        double confidence,
        String source,
        String evidence
) {
    public boolean isHighConfidence() { return confidence >= 0.75; }
    public boolean isUsable() { return confidence >= 0.3 && value != null; }

    /** Costruttore senza evidence (usato quando non si ha lo snippet grezzo). */
    public static <T> SourceFieldCandidate<T> of(
            String fieldName, T value, double confidence, String source) {
        return new SourceFieldCandidate<>(fieldName, value, confidence, source, null);
    }
}

package com.santofem.redditoauto.acquisition;

import java.util.List;

/**
 * Contratto per la normalizzazione dei candidati grezzi estratti dagli adapter.
 * Converte unità, uniforma enum (carburante, cambio), valuta, misure pneumatici.
 */
public interface FieldNormalizer {
    NormalizedFieldSet normalize(List<SourceFieldCandidate<?>> candidates);
}

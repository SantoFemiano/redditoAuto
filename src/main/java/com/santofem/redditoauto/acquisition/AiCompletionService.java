package com.santofem.redditoauto.acquisition;

import com.santofem.redditoauto.ai.dto.CarDataDTO;

import java.util.List;

/**
 * Contratto per il completamento AI dei campi mancanti.
 * L'AI interviene SOLO per i campi che il normalizzatore non è riuscito
 * a estrarre con sufficiente confidence, non come parser universale.
 */
public interface AiCompletionService {

    /**
     * Completa i campi mancanti usando l'AI.
     *
     * @param request      la richiesta originale (per contesto marca/modello/anno)
     * @param partialData  dati già estratti dagli adapter (da non sovrascrivere se high-confidence)
     * @param missingFields campi da richiedere specificamente all'AI
     * @param rawSnippet   testo grezzo disponibile dalla fonte (null se assente)
     * @return DTO completo con i campi AI-completati e quelli già noti
     */
    CarDataDTO completeMissingFields(
        CarLookupRequest request,
        NormalizedFieldSet partialData,
        List<String> missingFields,
        String rawSnippet
    );

    /**
     * Fallback completo: tutti i campi vengono richiesti all'AI senza testo di supporto.
     * Usato quando nessun adapter ha prodotto dati utili.
     */
    CarDataDTO fullAiExtraction(CarLookupRequest request);
}

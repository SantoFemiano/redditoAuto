package com.santofem.redditoauto.acquisition;

import com.santofem.redditoauto.ai.AiCarDataExtractor;
import com.santofem.redditoauto.ai.AiDirectDataProvider;
import com.santofem.redditoauto.ai.dto.CarDataDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementazione di AiCompletionService.
 * Usa AiCarDataExtractor quando c'è testo di supporto,
 * AiDirectDataProvider come fallback puro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAiCompletionService implements AiCompletionService {

    private static final int MAX_SNIPPET_CHARS = 3500;

    private final AiCarDataExtractor aiExtractor;
    private final AiDirectDataProvider aiDirectProvider;

    @Override
    public CarDataDTO completeMissingFields(
            CarLookupRequest request,
            NormalizedFieldSet partialData,
            List<String> missingFields,
            String rawSnippet) {

        log.info("[AI] Completamento guidato per {} - campi mancanti: {}",
            request.toLabel(), missingFields);

        if (rawSnippet != null && !rawSnippet.isBlank()) {
            String snippet = truncate(rawSnippet);
            log.debug("[AI] Uso AiCarDataExtractor con snippet {} chars", snippet.length());
            return aiExtractor.extractCarData(
                request.marca(), request.modello(), request.motore(),
                String.valueOf(request.anno()), snippet);
        }

        log.debug("[AI] Nessuno snippet disponibile: uso AiDirectDataProvider");
        return aiDirectProvider.getCarData(
            request.marca(), request.modello(), request.motore(),
            String.valueOf(request.anno()));
    }

    @Override
    public CarDataDTO fullAiExtraction(CarLookupRequest request) {
        log.info("[AI] Full extraction per {}", request.toLabel());
        return aiDirectProvider.getCarData(
            request.marca(), request.modello(), request.motore(),
            String.valueOf(request.anno()));
    }

    private String truncate(String text) {
        return text.length() <= MAX_SNIPPET_CHARS
            ? text
            : text.substring(0, MAX_SNIPPET_CHARS) + "...[TRONCATO]";
    }
}

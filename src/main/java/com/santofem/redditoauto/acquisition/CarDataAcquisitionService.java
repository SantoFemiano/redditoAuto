package com.santofem.redditoauto.acquisition;

import com.santofem.redditoauto.ai.dto.CarDataDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Coordina gli adapter di acquisizione.
 * Interroga tutte le fonti disponibili, normalizza i candidati,
 * valuta la confidence e chiama l'AI solo per i campi mancanti.
 *
 * Questo servizio è l'unico punto in cui avviene l'integrazione
 * tra scraping e AI: nessuna altra classe dovrebbe fare entrambe le cose.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarDataAcquisitionService {

    private final List<CarSourceAdapter> adapters;
    private final FieldNormalizer normalizer;
    private final FieldConfidenceService confidenceService;
    private final AiCompletionService aiCompletionService;

    /**
     * Acquisisce i dati per la richiesta.
     * Scorre gli adapter in ordine di registrazione, raccoglie tutti
     * i candidati, normalizza, valuta e completa con AI se necessario.
     *
     * @return AcquisitionResult con il DTO finale e metadati sulla fonte
     */
    public AcquisitionResult acquire(CarLookupRequest request) {
        log.info("[Acquisition] Avvio pipeline per {}", request.toLabel());

        List<SourceFieldCandidate<?>> allCandidates = new ArrayList<>();
        SourceExtractionResult bestResult = null;

        // 1. Interroga tutti gli adapter supportati
        for (CarSourceAdapter adapter : adapters) {
            if (!adapter.supports(request)) continue;

            log.debug("[Acquisition] Invocazione adapter: {}", adapter.sourceName());
            SourceExtractionResult result = adapter.extract(request);

            if (result.success() && !result.candidates().isEmpty()) {
                allCandidates.addAll(result.candidates());
                bestResult = result;
                log.info("[Acquisition] Adapter '{}': {} candidati estratti",
                    adapter.sourceName(), result.candidates().size());
            } else {
                log.warn("[Acquisition] Adapter '{}': nessun candidato ({})",
                    adapter.sourceName(),
                    result.warnings().isEmpty() ? "nessun warning" : result.warnings().get(0));
            }
        }

        // 2. Normalizza i candidati raccolti
        NormalizedFieldSet normalizedSet = normalizer.normalize(allCandidates);

        // 3. Anomalia check
        List<String> anomalies = confidenceService.detectAnomalies(normalizedSet);
        if (!anomalies.isEmpty()) {
            log.warn("[Acquisition] Anomalie nei dati estratti: {}", anomalies);
        }

        // 4. Identifica campi mancanti e chiama l'AI solo per quelli
        List<String> missingFields = confidenceService.getMissingOrLowConfidenceFields(normalizedSet);
        String rawSnippet = bestResult != null ? bestResult.rawSnippet() : null;

        CarDataDTO dto;
        String fonteDati;
        String warningAnno = null;
        int annoEffettivo = request.anno();

        if (bestResult != null && bestResult.hasRawSnippet()) {
            annoEffettivo = bestResult.annoEffettivo();
            warningAnno = bestResult.buildWarningAnno();

            if (!missingFields.isEmpty()) {
                log.info("[Acquisition] Completamento AI per campi mancanti: {}", missingFields);
                dto = aiCompletionService.completeMissingFields(
                    request, normalizedSet, missingFields, rawSnippet);
            } else {
                // Tutti i campi estratti con buona confidence: usa lo snippet per costruire il DTO
                dto = aiCompletionService.completeMissingFields(
                    request, normalizedSet, missingFields, rawSnippet);
            }
            fonteDati = "scraping:" + bestResult.source() + ":" + request.marca()
                + ":" + request.modello() + ":" + annoEffettivo;
        } else {
            // Nessun adapter ha prodotto dati: full AI extraction
            log.warn("[Acquisition] Nessun adapter ha prodotto dati. Full AI extraction per {}",
                request.toLabel());
            dto = aiCompletionService.fullAiExtraction(request);
            fonteDati = "ai-direct:" + request.marca() + ":" + request.modello() + ":" + request.anno();
        }

        return new AcquisitionResult(dto, fonteDati, warningAnno, annoEffettivo);
    }

    /**
     * Risultato dell'acquisizione con tutti i metadati necessari alla persistenza.
     */
    public record AcquisitionResult(
        CarDataDTO dto,
        String fonteDati,
        String warningAnno,
        int annoEffettivo
    ) {}
}

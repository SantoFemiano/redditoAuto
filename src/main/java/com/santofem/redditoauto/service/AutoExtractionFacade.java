package com.santofem.redditoauto.service;

import com.santofem.redditoauto.acquisition.CarDataAcquisitionService;
import com.santofem.redditoauto.acquisition.CarDataAcquisitionService.AcquisitionResult;
import com.santofem.redditoauto.acquisition.CarLookupRequest;
import com.santofem.redditoauto.ai.AiCarDataExtractor;
import com.santofem.redditoauto.ai.dto.CarDataDTO;
import com.santofem.redditoauto.controller.dto.MotorizzazioneResponseDTO;
import com.santofem.redditoauto.entity.Motorizzazione;
import com.santofem.redditoauto.exception.GeminiUnavailableException;
import com.santofem.redditoauto.mapper.CarDataMapper;
import com.santofem.redditoauto.scraper.WebScraper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Facade che il controller usa come unico punto d'ingresso.
 * Coordina:
 *  - MotorizzazioneLookupService  → cache/dedup DB
 *  - CarDataAcquisitionService     → pipeline adapter + AI completion
 *  - CarDataPersistenceService     → persistenza Marca/Modello/Motorizzazione
 *
 * Non contiene logica di business: è solo un coordinatore.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoExtractionFacade {

    private static final int MAX_TEXT_CHARS = 3500;

    private final MotorizzazioneLookupService lookupService;
    private final CarDataAcquisitionService acquisitionService;
    private final CarDataPersistenceService persistenceService;
    private final CarDataMapper carDataMapper;
    // Per il path /estrai-url e /estrai (testo grezzo) che bypassano la pipeline
    private final WebScraper webScraper;
    private final AiCarDataExtractor aiExtractor;

    // -----------------------------------------------
    // estrai-parametri (flusso principale)
    // -----------------------------------------------

    @Transactional
    public MotorizzazioneResponseDTO estraiDaParametri(
            String marca, String modello, String motore, int anno,
            int potenzaCv, String tipoCarburante, String tipoCambio) {

        log.info("[Facade] estrai-parametri: {} {} {} {} cv={} carb={} cambio={}",
            marca, modello, motore, anno, potenzaCv, tipoCarburante, tipoCambio);

        // 1. Cache DB
        Optional<Motorizzazione> cached = lookupService.findExisting(marca, modello, motore, anno);
        if (cached.isPresent()) {
            log.info("[Facade] Cache hit id={}", cached.get().getId());
            return carDataMapper.toResponseDTO(cached.get());
        }
        log.info("[Facade] Cache miss: avvio pipeline acquisizione per {} {} {}", marca, modello, motore);

        // 2. Pipeline acquisition (scraping → normalizzazione → AI completion)
        CarLookupRequest request = new CarLookupRequest(
            marca, modello, motore, anno, potenzaCv, tipoCarburante, tipoCambio);
        AcquisitionResult acquisition = callSafely(() -> acquisitionService.acquire(request));

        // 3. Override identità (i campi identitari vengono sempre da frontend)
        CarDataDTO dto = overrideIdentita(
            acquisition.dto(), marca, modello, motore, acquisition.annoEffettivo());

        // 4. Persistenza
        MotorizzazioneResponseDTO response = persistenceService.saveOrReturn(dto, acquisition.fonteDati());

        // 5. Propaga warning anno al frontend
        if (acquisition.warningAnno() != null) {
            response.setWarningAnno(acquisition.warningAnno());
            log.info("[Facade] Warning anno: {}", acquisition.warningAnno());
        }
        return response;
    }

    /** Overload per retrocompatibilità con chiamate senza hint motore. */
    @Transactional
    public MotorizzazioneResponseDTO estraiDaParametri(
            String marca, String modello, String motore, int anno) {
        return estraiDaParametri(marca, modello, motore, anno, 0, null, null);
    }

    // -----------------------------------------------
    // estrai-url
    // -----------------------------------------------

    @Transactional
    public MotorizzazioneResponseDTO estraiDaUrl(String url, String fonteDati) {
        log.info("[Facade] estrai-url: {}", url);
        String testo = webScraper.scrapeUrl(url)
            .orElseThrow(() ->
                new IllegalStateException("Impossibile estrarre testo dall'URL: " + url));
        CarDataDTO dto = callSafely(() -> aiExtractor.extractCarData(
            "sconosciuta", "sconosciuto", "sconosciuto", "0", trunca(testo)));
        return persistenceService.saveOrReturn(dto, fonteDati);
    }

    // -----------------------------------------------
    // estrai (testo grezzo)
    // -----------------------------------------------

    @Transactional
    public MotorizzazioneResponseDTO estraiDaTesto(String testoGrezzo, String fonteDati) {
        log.info("[Facade] estrai testo grezzo");
        CarDataDTO dto = callSafely(() -> aiExtractor.extractCarData(
            "sconosciuta", "sconosciuto", "sconosciuto", "0", trunca(testoGrezzo)));
        return persistenceService.saveOrReturn(dto, fonteDati);
    }

    // -----------------------------------------------
    // helpers
    // -----------------------------------------------

    private CarDataDTO overrideIdentita(
            CarDataDTO raw, String marca, String modello, String motore, int anno) {
        return new CarDataDTO(
            marca, modello, motore, anno,
            raw.tipoCarburante(), raw.tipoCambio(),
            raw.potenzaKw(), raw.potenzaCv(), raw.cilindrataCC(),
            raw.consumoMedioLitri100km(), raw.consumoUrbanoLitri100km(),
            raw.consumoExtraurbanoLitri100km(), raw.autonomiaKmElettrica(),
            raw.misuraPneumaticiAnteriori(), raw.misuraPneumaticiPosteriori(),
            raw.runFlat(), raw.prezzoListinoEur(),
            raw.costoTagliandoBaseEur(), raw.costoTagliandoMaiorEur(),
            raw.intervalloTagliandoKm(), raw.intervalloTagliandoMaiorKm(),
            raw.gruppoAssicurativo()
        );
    }

    private String trunca(String testo) {
        if (testo == null) return "";
        return testo.length() <= MAX_TEXT_CHARS ? testo : testo.substring(0, MAX_TEXT_CHARS);
    }

    private <T> T callSafely(Supplier<T> call) {
        try {
            return call.get();
        } catch (RuntimeException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            if (msg.contains("503") || msg.contains("UNAVAILABLE") || msg.contains("high demand")) {
                throw new GeminiUnavailableException(
                    "Il servizio AI e' temporaneamente sovraccarico. Riprova tra qualche secondo.", ex);
            }
            if (msg.contains("MalformedJson") || msg.contains("Unterminated string")) {
                throw new GeminiUnavailableException(
                    "L'AI ha restituito una risposta non valida (JSON troncato). Riprova.", ex);
            }
            throw ex;
        }
    }
}

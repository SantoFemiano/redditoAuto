package com.santofem.redditoauto.service;

import com.santofem.redditoauto.ai.AiCarDataExtractor;
import com.santofem.redditoauto.ai.AiDirectDataProvider;
import com.santofem.redditoauto.ai.dto.CarDataDTO;
import com.santofem.redditoauto.controller.dto.MotorizzazioneResponseDTO;
import com.santofem.redditoauto.entity.Marca;
import com.santofem.redditoauto.entity.Modello;
import com.santofem.redditoauto.entity.Motorizzazione;
import com.santofem.redditoauto.exception.GeminiUnavailableException;
import com.santofem.redditoauto.mapper.CarDataMapper;
import com.santofem.redditoauto.repository.MarcaRepository;
import com.santofem.redditoauto.repository.ModelloRepository;
import com.santofem.redditoauto.repository.MotorizzazioneRepository;
import com.santofem.redditoauto.scraper.WebScraper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Orchestratore del flusso completo di estrazione dati auto.
 *
 * FLUSSO:
 *   1. Cache DB check
 *   2. WebScraper.scrape()  → se OK: AiCarDataExtractor estrae dal testo
 *   3. Fallback AI-direct   → se scraping fallisce: AiDirectDataProvider
 *   4. isValid() + dedup + persist
 *
 * GESTIONE ERRORI AI:
 *   - RuntimeException con messaggio "503" → Gemini sovraccarico → GeminiUnavailableException → HTTP 503
 *   - DTO con campi null dopo chiamata AI → dati insufficienti → HTTP 422
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoExtractionOrchestrator {

    private final WebScraper webScraper;
    private final AiCarDataExtractor aiExtractor;
    private final AiDirectDataProvider aiDirectProvider;
    private final CarDataMapper carDataMapper;
    private final MarcaRepository marcaRepository;
    private final ModelloRepository modelloRepository;
    private final MotorizzazioneRepository motorizzazioneRepository;

    @Transactional
    public MotorizzazioneResponseDTO estraiDaParametri(
            String marca, String modello, String motore, int anno) {

        log.info("[Orchestratore] Richiesta estrazione: {} {} {} {}",
            marca, modello, motore, anno);

        // 1. Cache DB
        List<Motorizzazione> esistenti = motorizzazioneRepository
            .findByMarcaModelloAnno(marca, modello, anno);

        if (!esistenti.isEmpty()) {
            Optional<Motorizzazione> match = esistenti.stream()
                .filter(m -> motore.equalsIgnoreCase(m.getNomeMotore()))
                .findFirst();
            if (match.isPresent()) {
                log.info("[Orchestratore] Cache hit: motorizzazione id={}", match.get().getId());
                return carDataMapper.toResponseDTO(match.get());
            }
        }

        log.info("[Orchestratore] Cache miss: avvio scraping per {} {} {}", marca, modello, motore);

        // 2. Scraping web
        Optional<String> testoOpt = webScraper.scrape(marca, modello, motore, anno);

        CarDataDTO dto;
        String fonteDati;

        if (testoOpt.isPresent()) {
            // 3a. Scraping OK: AI estrae dal testo
            log.info("[Orchestratore] Scraping riuscito, invio testo all'AI extractor");
            dto = callAiSafely(() -> aiExtractor.extractCarData(testoOpt.get()));
            fonteDati = "scraping:" + marca + ":" + modello + ":" + anno;
        } else {
            // 3b. Fallback: AI genera direttamente
            log.warn("[Orchestratore] Scraping fallito. Fallback AI-direct per {} {} {} {}",
                marca, modello, motore, anno);
            dto = callAiSafely(() -> aiDirectProvider.getCarData(
                marca, modello, motore, String.valueOf(anno)));
            fonteDati = "ai-direct:" + marca + ":" + modello + ":" + anno;
        }

        return persistiDto(dto, fonteDati);
    }

    @Transactional
    public MotorizzazioneResponseDTO estraiDaTesto(String testoGrezzo, String fonteDati) {
        CarDataDTO dto = callAiSafely(() -> aiExtractor.extractCarData(testoGrezzo));
        return persistiDto(dto, fonteDati);
    }

    // -----------------------------------------------
    // WRAPPER AI: intercetta 503 prima che diventi DTO vuoto
    // -----------------------------------------------

    /**
     * Esegue una chiamata AI wrappandola in try/catch.
     * Se LangChain4j lancia RuntimeException con "503" nel messaggio
     * (dopo aver esaurito i retry), propaga GeminiUnavailableException
     * invece di tornare un DTO vuoto con null ovunque.
     */
    private CarDataDTO callAiSafely(java.util.function.Supplier<CarDataDTO> aiCall) {
        try {
            return aiCall.get();
        } catch (RuntimeException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            if (msg.contains("503") || msg.contains("UNAVAILABLE") || msg.contains("high demand")) {
                log.error("[AI] Gemini 503 dopo tutti i retry: {}", msg);
                throw new GeminiUnavailableException(
                    "Il servizio AI è temporaneamente sovraccarico. Riprova tra qualche secondo.", ex);
            }
            // Per altri errori AI (400, 401, ecc.) rilancia così com'è
            log.error("[AI] Errore chiamata Gemini: {}", msg);
            throw ex;
        }
    }

    // -----------------------------------------------
    // LOGICA COMUNE: validazione + dedup + persist
    // -----------------------------------------------

    private MotorizzazioneResponseDTO persistiDto(CarDataDTO dto, String fonteDati) {
        log.info("[AI] Estratto: marca='{}' modello='{}' motore='{}' anno={} carburante='{}' kw={}",
            dto.marca(), dto.modello(), dto.nomeMotore(), dto.annoProduzione(),
            dto.tipoCarburante(), dto.potenzaKw());

        if (!dto.isValid()) {
            log.warn("[AI] Dati insufficienti - salvataggio bloccato. " +
                     "DTO: marca='{}' modello='{}' kw={} carburante='{}'.",
                dto.marca(), dto.modello(), dto.potenzaKw(), dto.tipoCarburante());
            throw new IllegalStateException(
                "L'AI non ha estratto i campi minimi obbligatori. " +
                "Controlla che marca/modello/anno siano corretti."
            );
        }

        // Deduplicazione
        List<Motorizzazione> esistenti = motorizzazioneRepository
            .findByMarcaModelloAnno(dto.marca(), dto.modello(), dto.annoProduzione());
        if (!esistenti.isEmpty()) {
            log.info("[Orchestratore] Dedup: id={}", esistenti.get(0).getId());
            return carDataMapper.toResponseDTO(esistenti.get(0));
        }

        // Risolve/crea Marca
        Marca marca = marcaRepository.findByNomeIgnoreCase(dto.marca())
            .orElseGet(() -> {
                log.info("[DB] Creazione nuova marca: {}", dto.marca());
                return marcaRepository.save(
                    Marca.builder().nome(capitalizza(dto.marca())).build());
            });

        // Risolve/crea Modello
        Modello modello = modelloRepository
            .findByMarcaIdAndNomeIgnoreCase(marca.getId(), dto.modello())
            .orElseGet(() -> {
                log.info("[DB] Creazione nuovo modello: {} {}", dto.marca(), dto.modello());
                return modelloRepository.save(
                    Modello.builder()
                        .marca(marca)
                        .nome(capitalizza(dto.modello()))
                        .annoInizio(dto.annoProduzione())
                        .build());
            });

        Motorizzazione motorizzazione = carDataMapper.toEntity(dto, modello);
        motorizzazione.setFonteDati(fonteDati);
        Motorizzazione salvata = motorizzazioneRepository.save(motorizzazione);

        log.info("[DB] Nuova motorizzazione salvata id={} (fonte: {})", salvata.getId(), fonteDati);
        return carDataMapper.toResponseDTO(salvata);
    }

    private String capitalizza(String nome) {
        if (nome == null || nome.isBlank()) return nome;
        String t = nome.trim();
        return Character.toUpperCase(t.charAt(0)) + t.substring(1).toLowerCase();
    }
}

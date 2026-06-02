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
import java.util.function.Supplier;

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

    // -----------------------------------------------
    // ENDPOINT: /estrai-parametri (principale)
    // -----------------------------------------------

    @Transactional
    public MotorizzazioneResponseDTO estraiDaParametri(
            String marca, String modello, String motore, int anno) {

        log.info("[Orchestratore] Richiesta estrazione: {} {} {} {}",
            marca, modello, motore, anno);

        // 1. Cache DB — cerca per marca+modello+anno+motore (match esatto sul nome motore)
        List<Motorizzazione> esistenti = motorizzazioneRepository
            .findByMarcaModelloAnno(marca, modello, anno);

        if (!esistenti.isEmpty()) {
            // Prima cerca match esatto sul nomeMotore
            Optional<Motorizzazione> exactMatch = esistenti.stream()
                .filter(m -> motore.equalsIgnoreCase(m.getNomeMotore()))
                .findFirst();
            if (exactMatch.isPresent()) {
                log.info("[Orchestratore] Cache hit esatto: motorizzazione id={}", exactMatch.get().getId());
                return carDataMapper.toResponseDTO(exactMatch.get());
            }
            // Poi cerca match parziale (per gestire piccole differenze di naming)
            Optional<Motorizzazione> partialMatch = esistenti.stream()
                .filter(m -> m.getNomeMotore() != null
                    && !isPlaceholder(m.getNomeMotore())
                    && (motore.toLowerCase().contains(m.getNomeMotore().toLowerCase())
                        || m.getNomeMotore().toLowerCase().contains(motore.toLowerCase())))
                .findFirst();
            if (partialMatch.isPresent()) {
                log.info("[Orchestratore] Cache hit parziale: motorizzazione id={} nome='{}'",
                    partialMatch.get().getId(), partialMatch.get().getNomeMotore());
                return carDataMapper.toResponseDTO(partialMatch.get());
            }
        }

        log.info("[Orchestratore] Cache miss: avvio scraping per {} {} {}", marca, modello, motore);

        // 2. Scraping web
        Optional<String> testoOpt = webScraper.scrape(marca, modello, motore, anno);

        CarDataDTO dto;
        String fonteDati;

        if (testoOpt.isPresent()) {
            log.info("[Orchestratore] Scraping riuscito ({} chars), invio all'AI extractor",
                testoOpt.get().length());
            CarDataDTO raw = callAiSafely(() -> aiExtractor.extractCarData(
                marca, modello, motore, String.valueOf(anno), testoOpt.get()));
            dto = overrideIdentita(raw, marca, modello, motore, anno);
            fonteDati = "scraping:auto-data.net:" + marca + ":" + modello + ":" + anno;
        } else {
            log.warn("[Orchestratore] Scraping fallito. Fallback AI-direct per {} {} {} {}",
                marca, modello, motore, anno);
            CarDataDTO raw = callAiSafely(() -> aiDirectProvider.getCarData(
                marca, modello, motore, String.valueOf(anno)));
            dto = overrideIdentita(raw, marca, modello, motore, anno);
            fonteDati = "ai-direct:" + marca + ":" + modello + ":" + anno;
        }

        return persistiDto(dto, fonteDati);
    }

    // -----------------------------------------------
    // ENDPOINT: /estrai-url
    // -----------------------------------------------

    @Transactional
    public MotorizzazioneResponseDTO estraiDaUrl(String url, String fonteDati) {
        log.info("[Orchestratore] Estrazione da URL: {}", url);
        String testo = webScraper.scrapeUrl(url)
            .orElseThrow(() ->
                new IllegalStateException("Impossibile estrarre testo dall'URL: " + url));
        CarDataDTO dto = callAiSafely(() -> aiExtractor.extractCarData(
            "sconosciuta", "sconosciuto", "sconosciuto", "0", testo));
        return persistiDto(dto, fonteDati);
    }

    // -----------------------------------------------
    // ENDPOINT: /estrai (testo grezzo)
    // -----------------------------------------------

    @Transactional
    public MotorizzazioneResponseDTO estraiDaTesto(String testoGrezzo, String fonteDati) {
        CarDataDTO dto = callAiSafely(() -> aiExtractor.extractCarData(
            "sconosciuta", "sconosciuto", "sconosciuto", "0", testoGrezzo));
        return persistiDto(dto, fonteDati);
    }

    // -----------------------------------------------
    // OVERRIDE POST-AI
    // -----------------------------------------------

    /**
     * Sovrascrive sempre i campi identitari con i valori noti dal frontend.
     * Difesa principale contro placeholder letterali e valori errati da Gemini.
     */
    private CarDataDTO overrideIdentita(
            CarDataDTO raw, String marca, String modello, String motore, int anno) {

        boolean placeholderRilevato =
            isPlaceholder(raw.marca()) ||
            isPlaceholder(raw.modello()) ||
            isPlaceholder(raw.nomeMotore());

        if (placeholderRilevato) {
            log.warn("[AI] Placeholder rilevato nel DTO raw: marca='{}' modello='{}' motore='{}'. "
                   + "Override applicato con i valori frontend.",
                raw.marca(), raw.modello(), raw.nomeMotore());
        }

        return new CarDataDTO(
            marca,
            modello,
            motore,
            anno,
            raw.tipoCarburante(),
            raw.tipoCambio(),
            raw.potenzaKw(),
            raw.potenzaCv(),
            raw.cilindrataCC(),
            raw.consumoMedioLitri100km(),
            raw.consumoUrbanoLitri100km(),
            raw.consumoExtraurbanoLitri100km(),
            raw.autonomiaKmElettrica(),
            raw.misuraPneumaticiAnteriori(),
            raw.misuraPneumaticiPosteriori(),
            raw.runFlat(),
            raw.prezzoListinoEur(),
            raw.costoTagliandoBaseEur(),
            raw.costoTagliandoMaiorEur(),
            raw.intervalloTagliandoKm(),
            raw.intervalloTagliandoMaiorKm(),
            raw.gruppoAssicurativo()
        );
    }

    /**
     * Rileva placeholder del template o valori spazzatura.
     * Controlla: {campo}, {{campo}}, la stringa 'null', blank.
     */
    private boolean isPlaceholder(String value) {
        if (value == null || value.isBlank()) return true;
        String trimmed = value.trim();
        return trimmed.equalsIgnoreCase("null")
            || (trimmed.startsWith("{") && trimmed.endsWith("}"));
    }

    // -----------------------------------------------
    // WRAPPER AI
    // -----------------------------------------------

    private CarDataDTO callAiSafely(Supplier<CarDataDTO> aiCall) {
        try {
            return aiCall.get();
        } catch (RuntimeException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            if (msg.contains("503") || msg.contains("UNAVAILABLE") || msg.contains("high demand")) {
                log.error("[AI] Gemini 503 dopo tutti i retry: {}", msg);
                throw new GeminiUnavailableException(
                    "Il servizio AI e' temporaneamente sovraccarico. Riprova tra qualche secondo.", ex);
            }
            log.error("[AI] Errore chiamata Gemini: {}", msg);
            throw ex;
        }
    }

    // -----------------------------------------------
    // VALIDAZIONE + DEDUP + PERSIST
    // -----------------------------------------------

    private MotorizzazioneResponseDTO persistiDto(CarDataDTO dto, String fonteDati) {
        log.info("[AI] Estratto: marca='{}' modello='{}' motore='{}' anno={} carburante='{}' kw={}",
            dto.marca(), dto.modello(), dto.nomeMotore(), dto.annoProduzione(),
            dto.tipoCarburante(), dto.potenzaKw());

        if (!dto.isValid()) {
            log.warn("[AI] Dati insufficienti - salvataggio bloccato. "
                   + "DTO: marca='{}' modello='{}' kw={} carburante='{}'.",
                dto.marca(), dto.modello(), dto.potenzaKw(), dto.tipoCarburante());
            throw new IllegalStateException(
                "L'AI non ha estratto i campi minimi obbligatori. "
                + "Controlla che marca/modello/anno siano corretti."
            );
        }

        // Deduplicazione per marca+modello+anno+nomeMotore
        List<Motorizzazione> esistenti = motorizzazioneRepository
            .findByMarcaModelloAnno(dto.marca(), dto.modello(), dto.annoProduzione());
        if (!esistenti.isEmpty()) {
            Optional<Motorizzazione> dedup = esistenti.stream()
                .filter(m -> m.getNomeMotore() != null
                    && !isPlaceholder(m.getNomeMotore())
                    && (dto.nomeMotore().equalsIgnoreCase(m.getNomeMotore())
                        || dto.nomeMotore().toLowerCase().contains(m.getNomeMotore().toLowerCase())
                        || m.getNomeMotore().toLowerCase().contains(dto.nomeMotore().toLowerCase())))
                .findFirst();
            if (dedup.isPresent()) {
                log.info("[Orchestratore] Dedup per motore: id={} nome='{}'",
                    dedup.get().getId(), dedup.get().getNomeMotore());
                return carDataMapper.toResponseDTO(dedup.get());
            }
            // Se esistenti ma nessun match sul motore: procede con nuovo salvataggio
            log.info("[Orchestratore] Stessa marca/modello/anno ma motore diverso: procedo con nuovo salvataggio.");
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

        log.info("[DB] Nuova motorizzazione salvata id={} (fonte: {})",
            salvata.getId(), fonteDati);
        return carDataMapper.toResponseDTO(salvata);
    }

    private String capitalizza(String nome) {
        if (nome == null || nome.isBlank()) return nome;
        String t = nome.trim();
        return Character.toUpperCase(t.charAt(0)) + t.substring(1).toLowerCase();
    }
}

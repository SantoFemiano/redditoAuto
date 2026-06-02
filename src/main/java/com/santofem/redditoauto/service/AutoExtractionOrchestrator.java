package com.santofem.redditoauto.service;

import com.santofem.redditoauto.ai.AiCarDataExtractor;
import com.santofem.redditoauto.ai.dto.CarDataDTO;
import com.santofem.redditoauto.controller.dto.MotorizzazioneResponseDTO;
import com.santofem.redditoauto.entity.Marca;
import com.santofem.redditoauto.entity.Modello;
import com.santofem.redditoauto.entity.Motorizzazione;
import com.santofem.redditoauto.mapper.CarDataMapper;
import com.santofem.redditoauto.repository.MarcaRepository;
import com.santofem.redditoauto.repository.ModelloRepository;
import com.santofem.redditoauto.repository.MotorizzazioneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Orchestratore del flusso completo di estrazione dati auto:
 *   URL → WebScraperService → AiCarDataExtractor → CarDataMapper → DB
 *
 * Fattorizzato fuori dal Controller per riusabilità (es. job schedulati).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoExtractionOrchestrator {

    private final WebScraperService scraperService;
    private final AiCarDataExtractor aiExtractor;
    private final CarDataMapper carDataMapper;
    private final MarcaRepository marcaRepository;
    private final ModelloRepository modelloRepository;
    private final MotorizzazioneRepository motorizzazioneRepository;

    /**
     * Flusso completo da URL a DB:
     * 1. Scraping della pagina web
     * 2. Estrazione strutturata via AI
     * 3. Deduplicazione
     * 4. Persistenza
     *
     * @param url       URL della scheda tecnica
     * @param fonteDati Descrizione della fonte (può essere uguale all'URL)
     * @return MotorizzazioneResponseDTO della nuova entità o di quella già esistente
     */
    @Transactional
    public MotorizzazioneResponseDTO estraiDaUrl(String url, String fonteDati) {
        // Step 1: Scraping
        String testoGrezzo = scraperService.scaricaEPulisci(url);

        // Step 2: Estrazione AI
        return estraiDaTesto(testoGrezzo, fonteDati != null ? fonteDati : url);
    }

    /**
     * Flusso parziale: testo già disponibile (es. dall'utente o da una API JSON).
     */
    @Transactional
    public MotorizzazioneResponseDTO estraiDaTesto(String testoGrezzo, String fonteDati) {
        // Step 1: AI extraction
        CarDataDTO dto = aiExtractor.extractCarData(testoGrezzo);
        log.info("AI estratto: {} {} {} ({})",
            dto.marca(), dto.modello(), dto.nomeMotore(), dto.annoProduzione());

        validaEstrazioneMinima(dto);

        // Step 2: Deduplicazione
        List<Motorizzazione> esistenti = motorizzazioneRepository
            .findByMarcaModelloAnno(dto.marca(), dto.modello(), dto.annoProduzione());

        if (!esistenti.isEmpty()) {
            log.info("Motorizzazione già presente (id={}), salto inserimento",
                esistenti.getFirst().getId());
            return carDataMapper.toResponseDTO(esistenti.getFirst());
        }

        // Step 3: Risolve/crea Marca
        Marca marca = marcaRepository.findByNomeIgnoreCase(dto.marca())
            .orElseGet(() -> marcaRepository.save(
                Marca.builder().nome(normalizza(dto.marca())).build()));

        // Step 4: Risolve/crea Modello
        Modello modello = modelloRepository
            .findByMarcaIdAndNomeIgnoreCase(marca.getId(), dto.modello())
            .orElseGet(() -> modelloRepository.save(
                Modello.builder()
                    .marca(marca)
                    .nome(normalizza(dto.modello()))
                    .build()));

        // Step 5: Mappa e persiste
        Motorizzazione motorizzazione = carDataMapper.toEntity(dto, modello);
        motorizzazione.setFonteDati(fonteDati);
        Motorizzazione salvata = motorizzazioneRepository.save(motorizzazione);

        log.info("Nuova motorizzazione salvata con id={}", salvata.getId());
        return carDataMapper.toResponseDTO(salvata);
    }

    // -----------------------------------------------
    // PRIVATE
    // -----------------------------------------------

    /**
     * Verifica che l'AI abbia estratto almeno i campi obbligatori.
     * Previene inserimenti di record vuoti/inutilizzabili nel DB.
     */
    private void validaEstrazioneMinima(CarDataDTO dto) {
        if (dto.marca() == null || dto.marca().isBlank()) {
            throw new IllegalStateException(
                "L'AI non ha estratto la marca. Verifica che il testo contenga dati tecnici sufficienti.");
        }
        if (dto.modello() == null || dto.modello().isBlank()) {
            throw new IllegalStateException(
                "L'AI non ha estratto il modello.");
        }
        if (dto.potenzaKw() == null) {
            throw new IllegalStateException(
                "L'AI non ha estratto la potenza in kW. Necessaria per il calcolo del bollo.");
        }
    }

    /** Normalizza nome marca/modello: prima lettera maiuscola, resto minuscolo. */
    private String normalizza(String nome) {
        if (nome == null || nome.isBlank()) return nome;
        String trim = nome.trim();
        return Character.toUpperCase(trim.charAt(0)) + trim.substring(1).toLowerCase();
    }
}

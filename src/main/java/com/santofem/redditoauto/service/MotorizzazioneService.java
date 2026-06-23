package com.santofem.redditoauto.service;

import com.santofem.redditoauto.controller.dto.MotorizzazioneResponseDTO;
import com.santofem.redditoauto.entity.Motorizzazione;
import com.santofem.redditoauto.mapper.CarDataMapper;
import com.santofem.redditoauto.repository.MotorizzazioneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service per le operazioni CRUD e di ricerca sulle Motorizzazioni.
 *
 * Separato dall'AutoExtractionOrchestrator per rispettare
 * il Single Responsibility Principle:
 * - AutoExtractionOrchestrator: scraping + AI + persist
 * - MotorizzazioneService:      query + ricerca + autocomplete
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MotorizzazioneService {

    private final MotorizzazioneRepository motorizzazioneRepository;
    private final CarDataMapper carDataMapper;

    // -----------------------------------------------
    // READ
    // -----------------------------------------------

    /**
     * Recupera una motorizzazione per ID.
     *
     * @param id ID della motorizzazione
     * @return DTO della motorizzazione
     * @throws jakarta.persistence.EntityNotFoundException se non trovata
     */
    @Transactional(readOnly = true)
    public MotorizzazioneResponseDTO findById(Long id) {
        Motorizzazione m = motorizzazioneRepository.findById(id)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                "Motorizzazione non trovata con id: " + id));
        return carDataMapper.toResponseDTO(m);
    }

    /**
     * Ricerca motorizzazioni per marca, modello e anno.
     * Almeno uno dei parametri deve essere non-null.
     *
     * @param marca   es. "Volkswagen" (nullable)
     * @param modello es. "Golf" (nullable)
     * @param anno    es. 2022 (nullable)
     * @return lista di DTO corrispondenti
     */
    @Transactional(readOnly = true)
    public List<MotorizzazioneResponseDTO> search(
            String marca, String modello, Integer anno) {

        if (marca != null && modello != null && anno != null) {
            return motorizzazioneRepository
                .findByMarcaModelloAnno(marca, modello, anno)
                .stream()
                .map(carDataMapper::toResponseDTO)
                .toList();
        }

        // Fallback: tutti i record (paginazione gestita a livello Controller)
        return motorizzazioneRepository.findAll()
            .stream()
            .map(carDataMapper::toResponseDTO)
            .toList();
    }

    /**
     * Autocomplete per il frontend Angular: restituisce fino a 10 suggerimenti
     * basati su un testo parziale che corrisponde a marca o modello.
     *
     * Es. query="golf" -> ["Golf 1.5 TSI 2022", "Golf 2.0 TDI 2021", ...]
     *
     * @param query testo parziale digitato dall'utente
     * @return lista di stringhe di suggestion (max 10)
     */
    @Transactional(readOnly = true)
    public List<String> autocomplete(String query) {
        if (query == null || query.isBlank() || query.length() < 2) {
            return List.of();
        }

        return motorizzazioneRepository.findAll()
            .stream()
            .filter(m -> {
                String label = buildLabel(m).toLowerCase();
                return label.contains(query.toLowerCase().trim());
            })
            .limit(10)
            .map(this::buildLabel)
            .toList();
    }

    /**
     * Recupera tutte le motorizzazioni di un modello specifico.
     *
     * @param modelloId ID del Modello JPA
     * @return lista di DTO
     */
    @Transactional(readOnly = true)
    public List<MotorizzazioneResponseDTO> findByModello(Long modelloId) {
        return motorizzazioneRepository.findByModelloId(modelloId)
            .stream()
            .map(carDataMapper::toResponseDTO)
            .toList();
    }

    // -----------------------------------------------
    // WRITE
    // -----------------------------------------------

    /**
     * Marca una motorizzazione come confermata manualmente da un admin.
     * I dati confermati sono considerati affidabili e non vengono
     * ri-estratti dall'AI in futuro.
     *
     * @param id ID della motorizzazione da confermare
     * @return DTO aggiornato
     */
    @Transactional
    public MotorizzazioneResponseDTO confermaManualmente(Long id) {
        Motorizzazione m = motorizzazioneRepository.findById(id)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                "Motorizzazione non trovata con id: " + id));
        m.setConfermatoManualmente(true);
        log.info("[DB] Motorizzazione id={} confermata manualmente", id);
        return carDataMapper.toResponseDTO(motorizzazioneRepository.save(m));
    }

    /**
     * Elimina una motorizzazione dal DB.
     * Usato solo dagli amministratori per rimuovere record errati.
     *
     * @param id ID della motorizzazione da eliminare
     * @throws jakarta.persistence.EntityNotFoundException se non trovata
     */
    @Transactional
    public void delete(Long id) {
        if (!motorizzazioneRepository.existsById(id)) {
            throw new jakarta.persistence.EntityNotFoundException(
                "Motorizzazione non trovata con id: " + id);
        }
        motorizzazioneRepository.deleteById(id);
        log.info("[DB] Motorizzazione id={} eliminata", id);
    }

    // -----------------------------------------------
    // UTILITY
    // -----------------------------------------------

    /**
     * Costruisce la label leggibile per autocomplete.
     * Es. "Volkswagen Golf 2.0 TDI 150 CV DSG (2022)"
     */
    private String buildLabel(Motorizzazione m) {
        return String.format("%s %s %s (%d)",
            m.getModello().getMarca().getNome(),
            m.getModello().getNome(),
            m.getNomeMotore(),
            m.getAnnoProduzioneM());
    }
}

package com.santofem.redditoauto.controller;

import com.santofem.redditoauto.ai.AiCarDataExtractor;
import com.santofem.redditoauto.ai.dto.CarDataDTO;
import com.santofem.redditoauto.entity.Motorizzazione;
import com.santofem.redditoauto.mapper.CarDataMapper;
import com.santofem.redditoauto.repository.MarcaRepository;
import com.santofem.redditoauto.repository.ModelloRepository;
import com.santofem.redditoauto.repository.MotorizzazioneRepository;
import com.santofem.redditoauto.controller.dto.EstraiAutoRequestDTO;
import com.santofem.redditoauto.controller.dto.MotorizzazioneResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auto")
@RequiredArgsConstructor
@Slf4j
public class AutoController {

    private final AiCarDataExtractor aiExtractor;
    private final CarDataMapper carDataMapper;
    private final MarcaRepository marcaRepository;
    private final ModelloRepository modelloRepository;
    private final MotorizzazioneRepository motorizzazioneRepository;

    /**
     * POST /api/v1/auto/estrai
     * Accetta testo grezzo (scheda tecnica) e lo passa all'AI per estrarre
     * i dati strutturati, poi persiste la motorizzazione nel DB.
     *
     * Body: { "testoGrezzo": "...scheda tecnica...", "fonteDati": "https://..." }
     */
    @PostMapping("/estrai")
    @Transactional
    public ResponseEntity<MotorizzazioneResponseDTO> estraiEPersisti(
            @Valid @RequestBody EstraiAutoRequestDTO request) {

        log.info("Avvio estrazione AI da testo di {} caratteri", request.getTestoGrezzo().length());

        // 1. AI extraction: testo grezzo → CarDataDTO strutturato
        CarDataDTO dto = aiExtractor.extractCarData(request.getTestoGrezzo());
        log.info("AI ha estratto: {} {} {} ({})",
            dto.marca(), dto.modello(), dto.nomeMotore(), dto.annoProduzione());

        // 2. Controlla duplicati
        List<Motorizzazione> esistenti = motorizzazioneRepository
            .findByMarcaModelloAnno(dto.marca(), dto.modello(), dto.annoProduzione());

        if (!esistenti.isEmpty()) {
            log.info("Motorizzazione già presente, restituisco quella esistente (id={})",
                esistenti.getFirst().getId());
            return ResponseEntity.ok(
                carDataMapper.toResponseDTO(esistenti.getFirst()));
        }

        // 3. Risolve o crea Marca
        var marca = marcaRepository.findByNomeIgnoreCase(dto.marca())
            .orElseGet(() -> marcaRepository.save(
                com.santofem.redditoauto.entity.Marca.builder()
                    .nome(dto.marca()).build()));

        // 4. Risolve o crea Modello
        var modello = modelloRepository
            .findByMarcaIdAndNomeIgnoreCase(marca.getId(), dto.modello())
            .orElseGet(() -> modelloRepository.save(
                com.santofem.redditoauto.entity.Modello.builder()
                    .marca(marca)
                    .nome(dto.modello())
                    .build()));

        // 5. Mappa DTO → Entità e persiste
        Motorizzazione motorizzazione = carDataMapper.toEntity(dto, modello);
        motorizzazione.setFonteDati(request.getFonteDati());
        Motorizzazione salvata = motorizzazioneRepository.save(motorizzazione);

        log.info("Motorizzazione salvata con id={}", salvata.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(carDataMapper.toResponseDTO(salvata));
    }

    /**
     * GET /api/v1/auto/{id}
     * Restituisce il dettaglio di una motorizzazione per id.
     */
    @GetMapping("/{id}")
    public ResponseEntity<MotorizzazioneResponseDTO> getById(@PathVariable Long id) {
        return motorizzazioneRepository.findById(id)
            .map(m -> ResponseEntity.ok(carDataMapper.toResponseDTO(m)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/v1/auto?marca=VW&modello=Golf&anno=2022
     * Ricerca per marca + modello + anno.
     */
    @GetMapping
    public ResponseEntity<List<MotorizzazioneResponseDTO>> cerca(
            @RequestParam String marca,
            @RequestParam String modello,
            @RequestParam Integer anno) {

        List<MotorizzazioneResponseDTO> risultati = motorizzazioneRepository
            .findByMarcaModelloAnno(marca, modello, anno)
            .stream()
            .map(carDataMapper::toResponseDTO)
            .toList();

        return ResponseEntity.ok(risultati);
    }
}

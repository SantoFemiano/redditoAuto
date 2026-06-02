package com.santofem.redditoauto.controller;

import com.santofem.redditoauto.controller.dto.EstraiAutoRequestDTO;
import com.santofem.redditoauto.controller.dto.EstraiDaUrlRequestDTO;
import com.santofem.redditoauto.controller.dto.MotorizzazioneResponseDTO;
import com.santofem.redditoauto.mapper.CarDataMapper;
import com.santofem.redditoauto.repository.MotorizzazioneRepository;
import com.santofem.redditoauto.service.AutoExtractionOrchestrator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auto")
@RequiredArgsConstructor
@Slf4j
public class AutoController {

    private final AutoExtractionOrchestrator orchestrator;
    private final CarDataMapper carDataMapper;
    private final MotorizzazioneRepository motorizzazioneRepository;

    /**
     * POST /api/v1/auto/estrai
     * Accetta testo grezzo (incollato dall'utente o copiato da una scheda tecnica).
     */
    @PostMapping("/estrai")
    public ResponseEntity<MotorizzazioneResponseDTO> estraiDaTesto(
            @Valid @RequestBody EstraiAutoRequestDTO request) {
        MotorizzazioneResponseDTO risposta =
            orchestrator.estraiDaTesto(request.getTestoGrezzo(), request.getFonteDati());
        return ResponseEntity.status(HttpStatus.CREATED).body(risposta);
    }

    /**
     * POST /api/v1/auto/estrai-url
     * Accetta un URL: scraping automatico + AI extraction.
     * Body: { "url": "https://...", "urlFallback": "https://..." }
     */
    @PostMapping("/estrai-url")
    public ResponseEntity<MotorizzazioneResponseDTO> estraiDaUrl(
            @Valid @RequestBody EstraiDaUrlRequestDTO request) {
        MotorizzazioneResponseDTO risposta = orchestrator.estraiDaUrl(
            request.getUrl(), request.getUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(risposta);
    }

    /**
     * GET /api/v1/auto/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<MotorizzazioneResponseDTO> getById(@PathVariable Long id) {
        return motorizzazioneRepository.findById(id)
            .map(m -> ResponseEntity.ok(carDataMapper.toResponseDTO(m)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/v1/auto?marca=VW&modello=Golf&anno=2022
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

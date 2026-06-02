package com.santofem.redditoauto.controller;

import com.santofem.redditoauto.controller.dto.EstraiAutoRequestDTO;
import com.santofem.redditoauto.controller.dto.EstraiDaParametriRequestDTO;
import com.santofem.redditoauto.controller.dto.EstraiDaUrlRequestDTO;
import com.santofem.redditoauto.controller.dto.MotorizzazioneResponseDTO;
import com.santofem.redditoauto.service.AutoExtractionOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller per l'acquisizione dati auto tramite AI Extraction.
 *
 * Espone 3 entry-point:
 *   POST /estrai          → testo grezzo incollato dall'utente
 *   POST /estrai-url      → URL specifico (scraping + AI)
 *   POST /estrai-parametri → marca/modello/motore/anno + campi opzionali motore
 */
@RestController
@RequestMapping("/api/v1/auto")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Auto Extraction", description = "Acquisizione dati tecnici auto tramite AI")
public class AutoController {

    private final AutoExtractionOrchestrator orchestrator;

    @Operation(summary = "Estrai dati da testo grezzo",
               description = "Accetta testo libero da scheda tecnica e lo mappa tramite Gemini AI")
    @PostMapping("/estrai")
    public ResponseEntity<MotorizzazioneResponseDTO> estraiDaTesto(
            @Valid @RequestBody EstraiAutoRequestDTO request) {

        log.info("Richiesta estrazione da testo grezzo, fonte: {}", request.getFonteDati());
        MotorizzazioneResponseDTO risposta =
            orchestrator.estraiDaTesto(request.getTestoGrezzo(), request.getFonteDati());
        return ResponseEntity.status(HttpStatus.CREATED).body(risposta);
    }

    @Operation(summary = "Estrai dati da URL",
               description = "Scraping automatico dell'URL + estrazione Gemini AI")
    @PostMapping("/estrai-url")
    public ResponseEntity<MotorizzazioneResponseDTO> estraiDaUrl(
            @Valid @RequestBody EstraiDaUrlRequestDTO request) {

        log.info("Richiesta estrazione da URL: {}", request.getUrl());
        MotorizzazioneResponseDTO risposta =
            orchestrator.estraiDaUrl(request.getUrl(), request.getUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(risposta);
    }

    /**
     * POST /api/v1/auto/estrai-parametri
     *
     * Body minimo:
     * { "marca": "Volkswagen", "modello": "Golf", "motore": "2.0 TDI", "anno": 2022 }
     *
     * Body completo (migliora precisione motor-scoring):
     * {
     *   "marca": "Volkswagen", "modello": "Golf",
     *   "motore": "2.0 TDI", "anno": 2022,
     *   "potenzaCv": 150, "tipoCarburante": "DIESEL", "tipoCambio": "DSG"
     * }
     *
     * Se l'anno richiesto non ha una scheda disponibile, la response conterrà
     * il campo "warningAnno" con un messaggio da mostrare all'utente.
     */
    @Operation(summary = "Estrai dati da parametri auto",
               description = "Ricerca automatica sul web + estrazione Gemini AI. " +
                             "I campi potenzaCv, tipoCarburante, tipoCambio sono opzionali " +
                             "ma migliorano la precisione del match motorizzazione.")
    @PostMapping("/estrai-parametri")
    public ResponseEntity<MotorizzazioneResponseDTO> estraiDaParametri(
            @Valid @RequestBody EstraiDaParametriRequestDTO request) {

        log.info("Richiesta estrazione da parametri: {} {} {} ({}) cv={} carb={} cambio={}",
            request.getMarca(), request.getModello(), request.getMotore(), request.getAnno(),
            request.getPotenzaCv(), request.getTipoCarburante(), request.getTipoCambio());

        MotorizzazioneResponseDTO risposta = orchestrator.estraiDaParametri(
            request.getMarca(),
            request.getModello(),
            request.getMotore(),
            request.getAnno(),
            request.getPotenzaCv() != null ? request.getPotenzaCv() : 0,
            request.getTipoCarburante(),
            request.getTipoCambio()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(risposta);
    }
}

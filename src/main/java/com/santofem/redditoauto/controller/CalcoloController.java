package com.santofem.redditoauto.controller;

import com.santofem.redditoauto.service.CalcoloSostenibilitaService;
import com.santofem.redditoauto.service.dto.CalcoloRequestDTO;
import com.santofem.redditoauto.service.dto.CalcoloRispostaDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/calcolo")
@RequiredArgsConstructor
@Slf4j
public class CalcoloController {

    private final CalcoloSostenibilitaService calcoloService;

    /**
     * POST /api/v1/calcolo
     * Calcola la sostenibilità economica mensile completa per un'auto.
     *
     * Body: CalcoloRequestDTO
     * Response: CalcoloRispostaDTO con rata, costi vivi e giudizio
     */
    @PostMapping
    public ResponseEntity<CalcoloRispostaDTO> calcola(
            @Valid @RequestBody CalcoloRequestDTO request) {

        log.info("Richiesta calcolo sostenibilità per motorizzazioneId={}, reddito={}",
            request.getMotorizzazioneId(), request.getRedditoNettoMensile());

        CalcoloRispostaDTO risposta = calcoloService.calcola(request);
        return ResponseEntity.ok(risposta);
    }
}

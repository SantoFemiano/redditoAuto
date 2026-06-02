package com.santofem.redditoauto.controller;

import com.santofem.redditoauto.service.CalcoloSostenibilitaService;
import com.santofem.redditoauto.service.dto.CalcoloRequestDTO;
import com.santofem.redditoauto.service.dto.CalcoloRispostaDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller per il calcolo della sostenibilità economica mensile.
 *
 * Entry-point principale dell'applicazione:
 * l'utente ha già scelto l'auto (motorizzazioneId),
 * inserisce il reddito e i parametri di finanziamento,
 * e riceve il calcolo completo di rata + costi vivi + giudizio.
 */
@RestController
@RequestMapping("/api/v1/calcolo")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Calcolo Sostenibilità", description = "Calcola rata + costi vivi mensili e giudizio di sostenibilità")
public class CalcoloController {

    private final CalcoloSostenibilitaService calcoloService;

    /**
     * POST /api/v1/calcolo
     *
     * Calcola la sostenibilità economica completa per un'auto.
     * Richiede che la motorizzazione sia già presente nel DB
     * (pre-acquisita tramite AutoController).
     *
     * Body (CalcoloRequestDTO):
     * {
     *   "motorizzazioneId": 42,
     *   "redditoNettoMensile": 2000.00,
     *   "acconto": 3000.00,
     *   "durataFinanziamentoMesi": 60,
     *   "tanPercentuale": 7.5,
     *   "kmMensiliStimati": 1500,
     *   "prezzoCombustibileLitro": 1.85,
     *   "assicurazioneAnnuaEur": 850.00   // opzionale: se null viene stimata
     * }
     *
     * Response (CalcoloRispostaDTO):
     *   - Dettaglio di ogni voce di costo mensile
     *   - Totale mensile auto completo
     *   - Percentuale reddito impegnata
     *   - Giudizio: OTTIMO / ACCETTABILE / ATTENZIONE / CRITICO
     *   - Consigli personalizzati
     */
    @Operation(
        summary = "Calcola sostenibilità economica",
        description = "Restituisce rata, costi vivi mensilizzati, totale e giudizio di sostenibilità"
    )
    @PostMapping
    public ResponseEntity<CalcoloRispostaDTO> calcola(
            @Valid @RequestBody CalcoloRequestDTO request) {

        log.info("Richiesta calcolo sostenibilità → motorizzazioneId={}, reddito={}, durata={}m, TAN={}%",
            request.getMotorizzazioneId(),
            request.getRedditoNettoMensile(),
            request.getDurataFinanziamentoMesi(),
            request.getTanPercentuale());

        CalcoloRispostaDTO risposta = calcoloService.calcola(request);

        log.info("Calcolo completato → totale={} €/mese, giudizio={}, sostenibile={}",
            risposta.getTotaleMensileAutoCompleto(),
            risposta.getGiudizio(),
            risposta.isSostenibile());

        return ResponseEntity.ok(risposta);
    }
}

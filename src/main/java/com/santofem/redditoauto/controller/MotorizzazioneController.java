package com.santofem.redditoauto.controller;

import com.santofem.redditoauto.controller.dto.MotorizzazioneResponseDTO;
import com.santofem.redditoauto.service.MotorizzazioneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller per la consultazione del catalogo motorizzazioni già salvate.
 *
 * Espone endpoint di lettura/ricerca/autocomplete e operazioni admin.
 * Tutte le operazioni di scrittura (estrazione) passano da AutoController.
 */
@RestController
@RequestMapping("/api/v1/motorizzazioni")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Motorizzazioni", description = "Catalogo motorizzazioni salvate nel DB")
public class MotorizzazioneController {

    private final MotorizzazioneService motorizzazioneService;

    /**
     * GET /api/v1/motorizzazioni/{id}
     * Dettaglio completo di una motorizzazione salvata.
     */
    @Operation(summary = "Dettaglio motorizzazione per ID")
    @GetMapping("/{id}")
    public ResponseEntity<MotorizzazioneResponseDTO> getById(
            @Parameter(description = "ID della motorizzazione") @PathVariable Long id) {

        return ResponseEntity.ok(motorizzazioneService.findById(id));
    }

    /**
     * GET /api/v1/motorizzazioni?marca=VW&modello=Golf&anno=2022
     * Ricerca nel catalogo. Tutti i parametri sono opzionali.
     * Se non si fornisce nulla restituisce tutte le motorizzazioni salvate.
     */
    @Operation(summary = "Cerca motorizzazioni per marca/modello/anno")
    @GetMapping
    public ResponseEntity<List<MotorizzazioneResponseDTO>> search(
            @RequestParam(required = false) String marca,
            @RequestParam(required = false) String modello,
            @RequestParam(required = false) Integer anno) {

        log.debug("Ricerca motorizzazioni: marca={}, modello={}, anno={}", marca, modello, anno);
        return ResponseEntity.ok(motorizzazioneService.search(marca, modello, anno));
    }

    /**
     * GET /api/v1/motorizzazioni/autocomplete?q=golf
     * Autocomplete per l'Angular typeahead: restituisce lista di nomi completi.
     * Minimo 2 caratteri per evitare risposte troppo ampie.
     *
     * Response: [ "Volkswagen Golf 2.0 TDI 150CV (2022)", ... ]
     */
    @Operation(summary = "Autocomplete per la ricerca auto",
               description = "Restituisce nomi completi per il typeahead Angular. Min 2 caratteri.")
    @GetMapping("/autocomplete")
    public ResponseEntity<List<String>> autocomplete(
            @Parameter(description = "Query di ricerca (min 2 caratteri)")
            @RequestParam String q) {

        return ResponseEntity.ok(motorizzazioneService.autocomplete(q));
    }

    /**
     * GET /api/v1/motorizzazioni/modello/{modelloId}
     * Tutte le motorizzazioni di un determinato modello.
     * Utile per il dropdown "seleziona motore" nel frontend.
     */
    @Operation(summary = "Lista motorizzazioni per modello")
    @GetMapping("/modello/{modelloId}")
    public ResponseEntity<List<MotorizzazioneResponseDTO>> getByModello(
            @PathVariable Long modelloId) {

        return ResponseEntity.ok(motorizzazioneService.findByModello(modelloId));
    }

    // ─────────────────────────────────────────────
    // OPERAZIONI ADMIN
    // ─────────────────────────────────────────────

    /**
     * PATCH /api/v1/motorizzazioni/{id}/conferma
     * Marca una motorizzazione come "verificata manualmente".
     * Utile per il pannello admin: dopo la revisione umana, il flag
     * confermatoManualmente viene settato a true.
     */
    @Operation(summary = "[Admin] Conferma manuale motorizzazione",
               description = "Segna i dati come verificati da un operatore umano")
    @PatchMapping("/{id}/conferma")
    public ResponseEntity<MotorizzazioneResponseDTO> conferma(@PathVariable Long id) {
        log.info("Conferma manuale motorizzazione id={}", id);
        return ResponseEntity.ok(motorizzazioneService.confermaManualmente(id));
    }

    /**
     * DELETE /api/v1/motorizzazioni/{id}
     * Elimina un record errato dal catalogo.
     */
    @Operation(summary = "[Admin] Elimina motorizzazione")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.warn("Eliminazione motorizzazione id={}", id);
        motorizzazioneService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

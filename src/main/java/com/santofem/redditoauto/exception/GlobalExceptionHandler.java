package com.santofem.redditoauto.exception;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Gestore globale delle eccezioni.
 *
 * Mappatura eccezione → HTTP status:
 *   EntityNotFoundException  → 404 Not Found
 *   IllegalArgumentException → 400 Bad Request
 *   IllegalStateException    → 422 Unprocessable Entity
 *   MethodArgumentNotValid   → 400 con dettaglio per campo
 *   Exception (fallback)     → 500 Internal Server Error
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ─── 404 ─────────────────────────────────────────

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex) {
        log.warn("Entity non trovata: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    // ─── 400 ─────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Argomento non valido: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> erroriCampi = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String campo   = error instanceof FieldError fe ? fe.getField() : error.getObjectName();
            String messaggio = error.getDefaultMessage();
            erroriCampi.put(campo, messaggio);
        });

        log.warn("Errore di validazione: {}", erroriCampi);
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.ofValidation(erroriCampi));
    }

    // ─── 422 ─────────────────────────────────────────

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleUnprocessable(IllegalStateException ex) {
        log.warn("Stato non processabile: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse.of(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()));
    }

    // ─── 500 ─────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Errore imprevisto", ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR,
                "Errore interno del server. Contatta il supporto."));
    }

    // ─── Record risposta errore ───────────────────────

    public record ErrorResponse(
        int status,
        String error,
        String messaggio,
        LocalDateTime timestamp,
        Map<String, String> erroriCampi
    ) {
        static ErrorResponse of(HttpStatus status, String messaggio) {
            return new ErrorResponse(
                status.value(), status.getReasonPhrase(), messaggio,
                LocalDateTime.now(), null
            );
        }

        static ErrorResponse ofValidation(Map<String, String> erroriCampi) {
            return new ErrorResponse(
                400, "Bad Request", "Errori di validazione nei campi",
                LocalDateTime.now(), erroriCampi
            );
        }
    }
}

package com.santofem.redditoauto.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Gestore globale delle eccezioni — RFC 7807 ProblemDetail.
 * Tutte le risposte di errore hanno struttura uniforme:
 * {
 *   "type": "...",
 *   "title": "...",
 *   "status": 4xx/5xx,
 *   "detail": "...",
 *   "instance": "/api/...",
 *   "timestamp": "2026-..."
 * }
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String BASE_URI = "https://redditoauto.santofem.com/errors";

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleEntityNotFound(EntityNotFoundException ex, WebRequest request) {
        log.warn("Entità non trovata: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Risorsa non trovata");
        pd.setType(URI.create(BASE_URI + "/not-found"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Errore di validazione: {}", detail);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        pd.setTitle("Dati non validi");
        pd.setType(URI.create(BASE_URI + "/validation-error"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("Constraint violation: {}", detail);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        pd.setTitle("Vincolo di validazione violato");
        pd.setType(URI.create(BASE_URI + "/constraint-violation"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Argomento non valido: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Argomento non valido");
        pd.setType(URI.create(BASE_URI + "/bad-request"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * IllegalStateException → 422 Unprocessable Entity.
     *
     * Lanciata da CarDataPersistenceService quando il DTO estratto dall'AI
     * non contiene i campi minimi obbligatori (marca/modello/anno).
     * Restituire 500 sarebbe scorretto: si tratta di un errore di business
     * dovuto a dati insufficienti, non a un malfunzionamento del server.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        log.warn("Stato non valido (dati insufficienti): {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setTitle("Dati insufficienti per il salvataggio");
        pd.setType(URI.create(BASE_URI + "/unprocessable"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex, WebRequest request) {
        log.error("Errore non gestito: {}", ex.getMessage(), ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Si è verificato un errore interno. Riprova più tardi."
        );
        pd.setTitle("Errore interno del server");
        pd.setType(URI.create(BASE_URI + "/internal-error"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}

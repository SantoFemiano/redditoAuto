package com.santofem.redditoauto.exception;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handler globale per le eccezioni REST.
 * Usa il formato RFC 7807 (ProblemDetail) — nativo in Spring Boot 3+.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Errori di validazione (@Valid).
     * Restituisce un mappa campo → messaggio errore.
     * HTTP 400 Bad Request.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errori = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "valore non valido",
                (a, b) -> a
            ));

        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Errore di validazione");
        pd.setType(URI.create("https://redditoauto.local/errors/validation"));
        pd.setProperty("errori", errori);
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * Entità non trovata nel DB.
     * HTTP 404 Not Found.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleEntityNotFound(EntityNotFoundException ex) {
        log.warn("Entità non trovata: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setTitle("Risorsa non trovata");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create("https://redditoauto.local/errors/not-found"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * Errori logici (es. acconto > prezzo, dati AI incompleti).
     * HTTP 422 Unprocessable Entity.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Errore logico: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        pd.setTitle("Errore nella richiesta");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create("https://redditoauto.local/errors/unprocessable"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * Dati incompleti nel DB (es. prezzo listino mancante).
     * HTTP 409 Conflict.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        log.error("Stato inconsistente: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        pd.setTitle("Dati incompleti nel database");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create("https://redditoauto.local/errors/incomplete-data"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * Fallback generico.
     * HTTP 500 Internal Server Error.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Errore imprevisto", ex);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setTitle("Errore interno del server");
        pd.setDetail("Si è verificato un errore imprevisto. Riprova o contatta il supporto.");
        pd.setType(URI.create("https://redditoauto.local/errors/internal"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}

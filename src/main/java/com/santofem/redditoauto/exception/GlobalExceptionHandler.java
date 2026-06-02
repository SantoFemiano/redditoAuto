package com.santofem.redditoauto.exception;

import com.santofem.redditoauto.service.WebScraperService.WebScraperException;
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

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errori = ex.getBindingResult().getFieldErrors().stream()
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

    /** Scraping fallito (URL irraggiungibile, pagina SPA, HTTP error). */
    @ExceptionHandler(WebScraperException.class)
    public ProblemDetail handleScraper(WebScraperException ex) {
        log.error("Errore scraping: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY);
        pd.setTitle("Errore recupero dati dalla fonte esterna");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create("https://redditoauto.local/errors/scraping"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

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

package com.santofem.redditoauto.exception;

/**
 * Lanciata quando Gemini risponde con HTTP 503 (high demand / overload)
 * dopo aver esaurito tutti i retry.
 *
 * Viene intercettata dal GlobalExceptionHandler e mappata su HTTP 503
 * con un messaggio chiaro per il client.
 */
public class GeminiUnavailableException extends RuntimeException {

    public GeminiUnavailableException(String message) {
        super(message);
    }

    public GeminiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

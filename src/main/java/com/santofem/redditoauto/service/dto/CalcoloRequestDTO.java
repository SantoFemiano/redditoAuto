package com.santofem.redditoauto.service.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * DTO in ingresso per il calcolo di sostenibilità.
 * Contiene il reddito netto mensile dell'utente e i parametri
 * del finanziamento desiderato, più il riferimento all'auto scelta.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class CalcoloRequestDTO {

    // --- UTENTE ---

    @NotNull(message = "Il reddito netto mensile è obbligatorio")
    @DecimalMin(value = "0.01", message = "Il reddito deve essere positivo")
    private BigDecimal redditoNettoMensile;

    // --- AUTO ---

    @NotNull(message = "L'id della motorizzazione è obbligatorio")
    private Long motorizzazioneId;

    private Boolean isAutoUsata;
    private BigDecimal prezzoAutoUsata;

    @AssertTrue(message = "Se l'auto è usata, il prezzo dell'auto usata non può essere null")
    private boolean isPrezzoAutoUsataValid() {
        // Se isAutoUsata è true, il prezzo DEVE essere diverso da null.
        // In tutti gli altri casi (false o null), la validazione passa.
        if (Boolean.TRUE.equals(isAutoUsata)) {
            return prezzoAutoUsata != null;
        }
        return true;
    }


    // --- FINANZIAMENTO ---

    /** Acconto versato dall'utente (può essere 0). */
    @NotNull
    @DecimalMin(value = "0.0")
    @Builder.Default
    private BigDecimal acconto = BigDecimal.ZERO;

    /** Durata del finanziamento in mesi (es. 48, 60, 72). */
    @NotNull
    @Min(value = 12, message = "Durata minima: 12 mesi")
    @Max(value = 120, message = "Durata massima: 120 mesi")
    @Builder.Default
    private Integer durataFinanziamentoMesi = 60;

    /**
     * Tasso Annuo Nominale (TAN) in percentuale, es. 6.5 per 6.5%.
     * Valore di default: 7.0% (media finanziamento auto 2024 Italia).
     */
    @NotNull
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "30.0")
    @Builder.Default
    private BigDecimal tanPercentuale = new BigDecimal("7.0");

    // --- PARAMETRI DI USO ---

    /** Km percorsi al mese stimati dall'utente (default: 1500 km/mese). */
    @NotNull
    @Min(value = 100)
    @Max(value = 10000)
    @Builder.Default
    private Integer kmMensiliStimati = 1500;

    /**
     * Prezzo carburante al litro in euro (o €/kWh per elettrici).
     * Default: 1.85 €/L (media benzina/diesel Italia 2024).
     */
    @NotNull
    @DecimalMin(value = "0.01")
    @Builder.Default
    private BigDecimal prezzoCombustibileLitro = new BigDecimal("1.85");

    /**
     * Prezzo annuo assicurazione RC fornito dall'utente (opzionale).
     * Se null, verrà stimato dal gruppo assicurativo dell'auto.
     */
    private BigDecimal assicurazioneAnnuaEur;
}

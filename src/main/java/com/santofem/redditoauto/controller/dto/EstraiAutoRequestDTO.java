package com.santofem.redditoauto.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * DTO per il POST /api/v1/auto/estrai.
 *
 * Riceve testo grezzo (copia-incolla da scheda tecnica)
 * e una fonte opzionale per tracciabilità.
 *
 * Esempio body:
 * {
 *   "testoGrezzo": "Volkswagen Golf 2.0 TDI 150 CV, potenza 110 kW, consumo 5.5 l/100km...",
 *   "fonteDati": "https://www.autoscout24.it/scheda/golf-tdi"
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstraiAutoRequestDTO {

    @NotBlank(message = "Il testo da analizzare è obbligatorio")
    @Size(min = 50, message = "Il testo è troppo breve per estrarre dati affidabili (min 50 caratteri)")
    @Size(max = 20000, message = "Testo troppo lungo (max 20.000 caratteri)")
    private String testoGrezzo;

    /** URL o descrizione della fonte — opzionale, utile per tracciabilità dei dati. */
    private String fonteDati;
}

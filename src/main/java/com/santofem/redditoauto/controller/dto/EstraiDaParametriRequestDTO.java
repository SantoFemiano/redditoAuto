package com.santofem.redditoauto.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * DTO per il POST /api/v1/auto/estrai-parametri.
 *
 * Entry-point principale del frontend Angular:
 * l'utente seleziona marca/modello/motore/anno da dropdown
 * e il sistema cerca automaticamente le informazioni sul web.
 *
 * Esempio body:
 * {
 *   "marca": "Volkswagen",
 *   "modello": "Golf",
 *   "motore": "2.0 TDI 150CV DSG",
 *   "anno": 2022
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstraiDaParametriRequestDTO {

    @NotBlank(message = "La marca è obbligatoria")
    private String marca;

    @NotBlank(message = "Il modello è obbligatorio")
    private String modello;

    @NotBlank(message = "Il motore è obbligatorio")
    private String motore;

    @NotNull(message = "L'anno è obbligatorio")
    @Min(value = 1990, message = "Anno non valido: troppo vecchio")
    @Max(value = 2030, message = "Anno non valido: nel futuro")
    private Integer anno;
}

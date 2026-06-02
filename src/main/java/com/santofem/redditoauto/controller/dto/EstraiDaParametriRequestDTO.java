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
 * I campi opzionali (potenzaCv, tipoCarburante, tipoCambio) migliorano
 * la precisione del motor-scoring nello scraper.
 *
 * Esempio body completo:
 * {
 *   "marca": "Volkswagen",
 *   "modello": "Golf",
 *   "motore": "2.0 TDI 150CV DSG",
 *   "anno": 2022,
 *   "potenzaCv": 150,
 *   "tipoCarburante": "DIESEL",
 *   "tipoCambio": "DSG"
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

    /**
     * Potenza in CV dichiarata dall'utente (opzionale).
     * Se presente, migliora il motor-scoring evitando match con
     * motorizzazioni con potenza molto diversa.
     */
    @Min(value = 40,  message = "Potenza CV non realistica")
    @Max(value = 2000, message = "Potenza CV non realistica")
    private Integer potenzaCv;

    /**
     * Tipo carburante (es. BENZINA, DIESEL, IBRIDO, ELETTRICO, GPL, METANO).
     * Opzionale. Se presente, usato come filtro primario nel motor-scoring.
     */
    private String tipoCarburante;

    /**
     * Tipo cambio (es. MANUALE, DSG, AUTOMATICO, CVT, PDK, S-TRONIC).
     * Opzionale. Se presente, usato come filtro secondario nel motor-scoring.
     */
    private String tipoCambio;
}

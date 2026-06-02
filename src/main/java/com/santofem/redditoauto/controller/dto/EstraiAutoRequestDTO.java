package com.santofem.redditoauto.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstraiAutoRequestDTO {

    @NotBlank(message = "Il testo grezzo non può essere vuoto")
    @Size(min = 50, max = 20000, message = "Testo grezzo: tra 50 e 20000 caratteri")
    private String testoGrezzo;

    /** URL o descrizione della fonte (es. link AutoScout24, MotorTrend, ecc.) */
    private String fonteDati;
}

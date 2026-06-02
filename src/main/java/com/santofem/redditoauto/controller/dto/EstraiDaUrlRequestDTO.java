package com.santofem.redditoauto.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

/**
 * DTO per il POST /api/v1/auto/estrai-url.
 *
 * Riceve un URL da scrapare automaticamente.
 *
 * Esempio body:
 * {
 *   "url": "https://www.autoscout24.it/auto/volkswagen/golf/2022"
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstraiDaUrlRequestDTO {

    @NotBlank(message = "L'URL è obbligatorio")
    @Pattern(
        regexp = "^https?://.*",
        message = "L'URL deve iniziare con http:// o https://"
    )
    private String url;
}

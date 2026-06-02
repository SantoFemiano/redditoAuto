package com.santofem.redditoauto.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

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

    /** URL opzionale di fallback se il primario non è raggiungibile. */
    private String urlFallback;
}

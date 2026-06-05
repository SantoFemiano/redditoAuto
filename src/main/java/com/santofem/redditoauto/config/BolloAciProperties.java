package com.santofem.redditoauto.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Tariffe bollo ACI esternalizzate in application.properties.
 * Aggiornabile senza ricompilare il codice.
 * Prefix: reddito-auto.bollo
 */
@Validated
@ConfigurationProperties(prefix = "reddito-auto.bollo")
public record BolloAciProperties(
        @NotNull BigDecimal baseKw,
        @NotNull BigDecimal extraKw,
        @NotNull BigDecimal dieselBase,
        @NotNull BigDecimal dieselExtra,
        int sogliaKw,
        @NotNull BigDecimal minimo
) {}

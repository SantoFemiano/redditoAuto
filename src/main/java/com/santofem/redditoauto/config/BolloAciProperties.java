package com.santofem.redditoauto.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Tariffe Bollo ACI caricate da application.properties.
 * Aggiornabili senza ricompilare il codice.
 *
 * Esempio in application.properties:
 *   reddito-auto.bollo.base-kw=2.58
 *   reddito-auto.bollo.extra-kw=3.87
 *   reddito-auto.bollo.diesel-base=0.26
 *   reddito-auto.bollo.diesel-extra=0.39
 *   reddito-auto.bollo.soglia-kw=100
 *   reddito-auto.bollo.minimo=27.00
 */
@Validated
@ConfigurationProperties(prefix = "reddito-auto.bollo")
public record BolloAciProperties(

        /** Tariffa base €/kW per veicoli benzina/ibrido (fino a sogliaKw) */
        @NotNull @Positive
        BigDecimal baseKw,

        /** Tariffa €/kW per potenza eccedente sogliaKw (benzina/ibrido) */
        @NotNull @Positive
        BigDecimal extraKw,

        /** Maggiorazione diesel €/kW sulla tariffa base */
        @NotNull @Positive
        BigDecimal dieselBase,

        /** Maggiorazione diesel €/kW sulla tariffa extra */
        @NotNull @Positive
        BigDecimal dieselExtra,

        /** Soglia kW oltre la quale si applica la tariffa extra */
        @Min(1)
        int sogliaKw,

        /** Importo minimo annuo bollo (€) */
        @NotNull @Positive
        BigDecimal minimo

) {}

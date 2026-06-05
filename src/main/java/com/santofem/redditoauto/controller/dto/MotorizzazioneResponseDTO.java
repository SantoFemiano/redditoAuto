package com.santofem.redditoauto.controller.dto;

import java.math.BigDecimal;

/**
 * DTO di risposta per la motorizzazione — Java 25 record.
 * Immutabile, thread-safe, zero boilerplate.
 * Mappato da MapStruct in MotorizzazioneMapper.
 */
public record MotorizzazioneResponseDTO(
        Long id,
        String nomeMarca,
        String nomeModello,
        String nomeMotore,
        Integer annoProduzione,
        Integer potenzaKw,
        Integer potenzaCv,
        String tipoCarburante,
        String tipoCambio,
        BigDecimal consumoMedioLitri100km,
        BigDecimal prezzoListinoEur,
        Boolean runFlat,
        Integer gruppoAssicurativo
) {}

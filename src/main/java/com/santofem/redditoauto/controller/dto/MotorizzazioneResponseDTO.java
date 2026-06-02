package com.santofem.redditoauto.controller.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO di risposta che espone i dati di una Motorizzazione
 * senza esporre i dettagli interni dell'entità JPA.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MotorizzazioneResponseDTO {

    private Long    id;
    private String  marca;
    private String  modello;
    private String  nomeMotore;
    private Integer annoProduzione;
    private String  tipoCarburante;
    private String  tipoCambio;
    private Integer potenzaKw;
    private Integer potenzaCv;
    private Integer cilindrataCC;

    private BigDecimal consumoMedioLitri100km;
    private BigDecimal consumoUrbanoLitri100km;
    private BigDecimal consumoExtraurbanoLitri100km;
    private Integer    autonomiaKmElettrica;

    private String  misuraPneumaticiAnteriori;
    private String  misuraPneumaticiPosteriori;
    private Boolean runFlat;

    private BigDecimal prezzoListinoEur;
    private BigDecimal costoTagliandoBaseEur;
    private BigDecimal costoTagliandoMaiorEur;
    private Integer    intervalloTagliandoKm;
    private Integer    intervalloTagliandoMaiorKm;
    private Integer    gruppoAssicurativo;

    private String        fonteDati;
    private LocalDateTime dataEstrazione;
    private Boolean       confermatoManualmente;
}

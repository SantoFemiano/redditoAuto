package com.santofem.redditoauto.controller.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO di risposta REST per una Motorizzazione.
 *
 * Usato da:
 * - CarDataMapper.toResponseDTO(Motorizzazione) per costruire la risposta
 * - MotorizzazioneController (blocco 10) per gli endpoint di ricerca/autocomplete
 *
 * Gli enum (tipoCarburante, tipoCambio) sono esposti come String
 * per semplicita' di serializzazione JSON verso il frontend Angular.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MotorizzazioneResponseDTO {

    // --- IDENTIFICAZIONE ---
    private Long    id;
    private String  marca;
    private String  modello;
    private String  nomeMotore;       // es. "2.0 TDI 150 CV DSG"
    private Integer annoProduzione;

    // --- MOTORE ---
    private String  tipoCarburante;   // es. "DIESEL" (enum.name())
    private String  tipoCambio;       // es. "DCT"
    private Integer potenzaKw;
    private Integer potenzaCv;
    private Integer cilindrataCC;

    // --- CONSUMI ---
    private BigDecimal consumoMedioLitri100km;
    private BigDecimal consumoUrbanoLitri100km;
    private BigDecimal consumoExtraurbanoLitri100km;
    private Integer    autonomiaKmElettrica;

    // --- PNEUMATICI ---
    private String  misuraPneumaticiAnteriori;
    private String  misuraPneumaticiPosteriori;
    private Boolean runFlat;

    // --- COSTI ---
    private BigDecimal prezzoListinoEur;
    private BigDecimal costoTagliandoBaseEur;
    private BigDecimal costoTagliandoMaiorEur;
    private Integer    intervalloTagliandoKm;
    private Integer    intervalloTagliandoMaiorKm;
    private Integer    gruppoAssicurativo;

    // --- METADATA ---
    private String        fonteDati;
    private LocalDateTime dataEstrazione;
    private Boolean       confermatoManualmente;
}

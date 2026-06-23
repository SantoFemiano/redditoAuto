package com.santofem.redditoauto.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO di risposta per l'estrazione dati auto.
 *
 * Contiene:
 * - I dati tecnici della motorizzazione estratti dall'AI
 * - Metadati dell'estrazione (fonte, warning anno)
 * - Prezzo e fonte scraping (popolati solo se estratti via /estrai-url)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MotorizzazioneResponseDTO {

    // ── Identità veicolo ─────────────────────────────────────────────────────
    private Long   id;
    private String marca;
    private String modello;
    private String nomeMotore;
    private int annoProduzione;
    private int annoFineProduzione;
    private int annoProduzioneM;
    private int annoFineProduzioneM;

    // ── Motorizzazione ────────────────────────────────────────────────────────
    private String  tipoCarburante;
    private String  tipoCambio;
    private Double  potenzaKw;
    private Integer potenzaCv;
    private Integer cilindrataCC;

    // ── Consumi ───────────────────────────────────────────────────────────────
    private BigDecimal consumoMedioLitri100km;
    private Double consumoUrbanoLitri100km;
    private Double consumoExtraurbanoLitri100km;
    private Double autonomiaKmElettrica;

    // ── Pneumatici ────────────────────────────────────────────────────────────
    private String  misuraPneumaticiAnteriori;
    private String  misuraPneumaticiPosteriori;
    private Boolean runFlat;
    private Integer kmDurataPneumatici;

    // ── Costi ─────────────────────────────────────────────────────────────────
    private Double  costoTagliandoBaseEur;
    private Double  costoTagliandoMaiorEur;
    private Integer intervalloTagliandoKm;
    private Integer intervalloTagliandoMaiorKm;
    private Integer gruppoAssicurativo;

    // ── Prezzo (popolato solo da /estrai-url con siti che lo espongono) ───────
    /** Prezzo di listino in EUR trovato dallo scraper. Null se non disponibile. */
    private Double  prezzoListinoEur;

    /** Nome del sito da cui è stato estratto il prezzo. Null se non disponibile. */
    private String  fonteScraping;

    // ── Metadati estrazione ───────────────────────────────────────────────────
    private String fonteDati;
    private LocalDateTime dataEstrazione;
    private Boolean confermatoManualmente;


    /**
     * Warning da mostrare all'utente quando l'anno richiesto non aveva
     * una scheda tecnica disponibile e si è usato un anno alternativo.
     * Null se non applicabile.
     */
    private String warningAnno;
}

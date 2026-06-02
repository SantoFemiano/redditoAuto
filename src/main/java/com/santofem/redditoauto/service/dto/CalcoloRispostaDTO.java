package com.santofem.redditoauto.service.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * DTO di risposta con il dettaglio completo del calcolo di sostenibilità.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalcoloRispostaDTO {

    // --- AUTO IDENTIFICATA ---
    private String marcaModelloMotore;       // es. "Volkswagen Golf 2.0 TDI 150 CV DSG (2022)"

    // --- RATA FINANZIAMENTO ---
    private BigDecimal prezzoFinanziato;     // Prezzo listino - acconto
    private BigDecimal rataFiananziamentoMensile; // Rata mensile (formula francese)
    private Integer    durataFinanziamentoMesi;
    private BigDecimal tanPercentuale;
    private BigDecimal costoTotaleFinanziamento; // Totale pagato a fine piano
    private BigDecimal interessiTotali;          // Costo puro degli interessi

    // --- COSTI VIVI MENSILI ---
    private BigDecimal costoCarburanteMensile;   // kmMensili * (consumo/100) * prezzoCombustibile
    private BigDecimal costoPneumaticiMensile;   // Mensilizzato su 4 anni (o 3 se run-flat)
    private BigDecimal costoTagliandoMensile;    // Mensilizzato sull'intervallo tagliando
    private BigDecimal costoTagliandoMaiorMensile; // Mensilizzato sull'intervallo maggiore
    private BigDecimal costoBolloMensile;        // Bollo ACI annuo / 12
    private BigDecimal costoAssicurazioneMensile; // RC Auto annua / 12

    // --- TOTALI ---
    private BigDecimal totaleCostiViviMensili;   // Somma di tutti i costi vivi (NO rata)
    private BigDecimal totaleMensileAutoCompleto; // Rata + tutti i costi vivi

    // --- SOSTENIBILITÀ ---
    private BigDecimal redditoNettoMensile;
    private BigDecimal percentualeRedditoImpegnata; // totaleMensile / reddito * 100
    private boolean    sostenibile;                 // true se < soglia (default 30%)
    private String     giudizio;                    // "OTTIMO", "ACCETTABILE", "ATTENZIONE", "CRITICO"
    private String     messaggioConsigli;           // Testo descrittivo con consigli
}

package com.santofem.redditoauto.dto;

import java.math.BigDecimal;

/**
 * DTO di output del CalcoloSostenibilitaService.
 * Contiene il dettaglio completo dei costi mensili e il giudizio di sostenibilità.
 */
public class RisultatoCalcoloDTO {

    // --- Costi mensili dettagliati ---
    private BigDecimal rata;
    private BigDecimal costoCarburanteMensile;
    private BigDecimal costoPneumaticiMensile;
    private BigDecimal costoTagliandiMensile;
    private BigDecimal bolloMensile;
    private BigDecimal assicurazioneMensile;

    // --- Totali ---
    private BigDecimal totaleCostoMensile;
    private BigDecimal redditoNetto;
    private BigDecimal percentualeSuReddito;   // % del reddito assorbita dall'auto
    private boolean    sostenibile;             // true se percentuale <= 30%
    private int        kmMensiliUsati;          // km usati nel calcolo

    private RisultatoCalcoloDTO() {}

    // ========================
    // BUILDER
    // ========================
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final RisultatoCalcoloDTO dto = new RisultatoCalcoloDTO();

        public Builder rata(BigDecimal v)                    { dto.rata = v;                    return this; }
        public Builder costoCarburanteMensile(BigDecimal v)  { dto.costoCarburanteMensile = v;  return this; }
        public Builder costoPneumaticiMensile(BigDecimal v)  { dto.costoPneumaticiMensile = v;  return this; }
        public Builder costoTagliandiMensile(BigDecimal v)   { dto.costoTagliandiMensile = v;   return this; }
        public Builder bolloMensile(BigDecimal v)            { dto.bolloMensile = v;            return this; }
        public Builder assicurazioneMensile(BigDecimal v)    { dto.assicurazioneMensile = v;    return this; }
        public Builder totaleCostoMensile(BigDecimal v)      { dto.totaleCostoMensile = v;      return this; }
        public Builder redditoNetto(BigDecimal v)            { dto.redditoNetto = v;            return this; }
        public Builder percentualeSuReddito(BigDecimal v)    { dto.percentualeSuReddito = v;    return this; }
        public Builder sostenibile(boolean v)                { dto.sostenibile = v;             return this; }
        public Builder kmMensiliUsati(int v)                 { dto.kmMensiliUsati = v;          return this; }

        public RisultatoCalcoloDTO build() { return dto; }
    }

    // ========================
    // GETTERS
    // ========================
    public BigDecimal getRata()                   { return rata; }
    public BigDecimal getCostoCarburanteMensile() { return costoCarburanteMensile; }
    public BigDecimal getCostoPneumaticiMensile() { return costoPneumaticiMensile; }
    public BigDecimal getCostoTagliandiMensile()  { return costoTagliandiMensile; }
    public BigDecimal getBolloMensile()           { return bolloMensile; }
    public BigDecimal getAssicurazioneMensile()   { return assicurazioneMensile; }
    public BigDecimal getTotaleCostoMensile()     { return totaleCostoMensile; }
    public BigDecimal getRedditoNetto()           { return redditoNetto; }
    public BigDecimal getPercentualeSuReddito()   { return percentualeSuReddito; }
    public boolean    isSostenibile()             { return sostenibile; }
    public int        getKmMensiliUsati()         { return kmMensiliUsati; }
}

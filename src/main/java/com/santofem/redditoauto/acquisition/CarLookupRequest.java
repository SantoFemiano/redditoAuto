package com.santofem.redditoauto.acquisition;

/**
 * Contratto d'ingresso della pipeline di acquisizione dati.
 * Tutti gli adapter e i servizi parlano con questo record.
 */
public record CarLookupRequest(
        String marca,
        String modello,
        String motore,
        int anno,
        Integer potenzaCv,
        String tipoCarburante,
        String tipoCambio
) {
    /** Factory per richiesta minimale (senza hint motore). */
    public static CarLookupRequest of(String marca, String modello, String motore, int anno) {
        return new CarLookupRequest(marca, modello, motore, anno, null, null, null);
    }

    public String toLabel() {
        return marca + " " + modello + " " + motore + " " + anno;
    }
}

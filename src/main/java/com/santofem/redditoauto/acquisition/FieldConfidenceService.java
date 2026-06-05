package com.santofem.redditoauto.acquisition;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Assegna e valida i confidence score per i campi normalizzati.
 * Segnala anomalie e range sospetti prima che i dati arrivino all'AI.
 */
@Slf4j
@Service
public class FieldConfidenceService {

    /** Campi obbligatori per considerare un set "sufficiente" senza AI. */
    private static final List<String> REQUIRED_FIELDS = List.of(
        "potenzaKw", "potenzaCv", "tipoCarburante"
    );

    /** Campi che vale la pena completare con l'AI se mancano. */
    public static final List<String> AI_COMPLETABLE_FIELDS = List.of(
        "consumoMedio", "consumoUrbano", "consumoExtraurbano",
        "cilindrataCC", "gruppoAssicurativo", "intervalloTagliandoKm",
        "costoTagliandoBase", "misuraPneumaticiAnt", "misuraPneumaticiPost"
    );

    /**
     * Valuta il set normalizzato e restituisce i nomi dei campi mancanti
     * o a bassa confidence che l'AI dovrebbe completare.
     */
    public List<String> getMissingOrLowConfidenceFields(NormalizedFieldSet fieldSet) {
        List<String> missing = new ArrayList<>();
        for (String field : AI_COMPLETABLE_FIELDS) {
            if (fieldSet.isMissing(field) || fieldSet.isLowConfidence(field)) {
                missing.add(field);
            }
        }
        return missing;
    }

    /**
     * Verifica se il set ha almeno i campi minimi per procedere senza AI.
     */
    public boolean hasSufficientData(NormalizedFieldSet fieldSet) {
        return REQUIRED_FIELDS.stream().noneMatch(fieldSet::isMissing);
    }

    /**
     * Segnala anomalie sui valori (valori sospetti fuori dal range tipico).
     */
    public List<String> detectAnomalies(NormalizedFieldSet fieldSet) {
        List<String> anomalies = new ArrayList<>();

        fieldSet.get("potenzaKw", Double.class).ifPresent(kw -> {
            if (kw < 30 || kw > 600)
                anomalies.add("potenzaKw=" + kw + " sembra anomala");
        });

        fieldSet.get("consumoMedio", Double.class).ifPresent(cons -> {
            if (cons < 0.5 || cons > 30)
                anomalies.add("consumoMedio=" + cons + " l/100km sembra anomalo");
        });

        fieldSet.get("cilindrataCC", Double.class).ifPresent(cc -> {
            if (cc < 500 || cc > 8000)
                anomalies.add("cilindrataCC=" + cc + " sembra anomala");
        });

        if (!anomalies.isEmpty()) {
            log.warn("[FieldConfidence] Anomalie rilevate: {}", anomalies);
        }
        return anomalies;
    }
}

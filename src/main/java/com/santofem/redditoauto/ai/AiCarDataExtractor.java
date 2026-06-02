package com.santofem.redditoauto.ai;

import com.santofem.redditoauto.ai.dto.CarDataDTO;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AiService LangChain4j per l'estrazione strutturata di dati tecnici auto.
 *
 * PATTERN: Information Extractor (RAG-light)
 * ============================================
 * 1. Il WebScraperService recupera il testo grezzo (HTML pulito) da fonti esterne.
 * 2. Il testo viene passato a questo service come {rawText}.
 * 3. LangChain4j costruisce il JSON Schema da CarDataDTO e lo invia a Gemini
 *    insieme al SystemMessage e al testo grezzo.
 * 4. Gemini risponde con un JSON strutturato.
 * 5. LangChain4j deserializza il JSON in un CarDataDTO fortemente tipizzato.
 *
 * ANTI-ALLUCINAZIONE:
 * - temperature=0.0 sul modello (LangChain4jConfig)
 * - Il SystemMessage vieta esplicitamente di inventare dati
 * - I campi assenti nel testo devono essere null, non stimati
 *
 * NOTA sul template variable:
 * LangChain4j usa la sintassi {variabile} con @V("variabile").
 * NON usare la sintassi Mustache {{variabile}} qui.
 */
public interface AiCarDataExtractor {

    @SystemMessage("""
        Sei un sistema di estrazione dati specializzato in schede tecniche di autoveicoli.
        Il tuo unico compito e' leggere il testo grezzo fornito e mappare
        ESCLUSIVAMENTE le informazioni presenti in quel testo nei campi del JSON di output.

        REGOLE FONDAMENTALI — rispettale sempre:
        1. NON inventare, stimare o dedurre valori non esplicitamente presenti nel testo.
        2. Se un'informazione non e' nel testo, il campo deve essere null.
        3. Per i consumi: usa l/100km. Se trovi km/l, converti con: consumo = 100 / (km/l).
           Esempio: 18 km/l -> 5.56 l/100km.
        4. Estrai sia kW che CV se entrambi presenti nel testo.
        5. Per i pneumatici usa il formato: LARGHEZZA/PROFILO RZEPPA es. '205/55 R16'.
        6. tipoCarburante: mappa a uno di BENZINA, DIESEL, GPL, METANO,
           IBRIDO_BENZINA, IBRIDO_DIESEL, IBRIDO_PLUGIN, ELETTRICO, IDROGENO.
        7. tipoCambio: mappa a uno di MANUALE, AUTOMATICO_TRADIZIONALE, DCT, CVT, SINGOLA_MARCIA.
        8. Prezzi in euro come numero decimale, senza simboli (es. 32500.0 non '32.500 euro').
        9. Restituisci SOLO il JSON strutturato, nessun testo aggiuntivo.
        """)
    @UserMessage("""
        Estrai i dati tecnici da questo testo grezzo relativo a un autoveicolo.
        Popola i campi con i dati esplicitamente presenti nel testo.
        Imposta null per i campi non trovati. Non inventare nulla.

        TESTO GREZZO:
        {rawText}
        """)
    CarDataDTO extractCarData(@V("rawText") String rawText);
}

package com.santofem.redditoauto.ai;

import com.santofem.redditoauto.ai.dto.CarDataDTO;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AiService LangChain4j per l'estrazione strutturata di dati da testo grezzo.
 *
 * Pattern: Information Extractor (RAG-light).
 * Il testo grezzo viene prodotto da un WebScraperService che recupera
 * schede tecniche da fonti esterne (AutoScout24, MotorTrend, ecc.).
 * Gemini mappa il testo -> CarDataDTO senza inventare dati.
 *
 * Registrare come Bean tramite AiServices.builder() nella @Configuration.
 */
public interface AiCarDataExtractor {

    @SystemMessage("""
        Sei un sistema di estrazione dati specializzato in schede tecniche di autoveicoli.
        Il tuo unico compito è leggere il testo grezzo fornito e mappare
        ESCLUSIVAMENTE le informazioni presenti in quel testo nei campi del JSON di output.

        REGOLE FONDAMENTALI:
        1. NON inventare, stimare o dedurre valori non esplicitamente presenti nel testo.
        2. Se un'informazione non è presente nel testo, imposta il campo a null.
        3. Per i consumi, usa sempre l/100km. Se trovi km/l, converti (es. 18 km/l = 5.56 l/100km).
        4. Per la potenza, estrai sia kW che CV se presenti.
        5. Per i pneumatici, usa il formato standard: 'LARGHEZZA/PROFILO RZEPPA' es. '205/55 R16'.
        6. Per tipoCarburante e tipoCambio, mappa al valore enum più vicino tra:
           tipoCarburante: BENZINA, DIESEL, GPL, METANO, IBRIDO_BENZINA, IBRIDO_DIESEL, IBRIDO_PLUGIN, ELETTRICO
           tipoCambio: MANUALE, AUTOMATICO_TRADIZIONALE, DCT, CVT, SINGOLA_MARCIA
        7. I prezzi devono essere in euro, senza simboli di valuta.
        8. Sii preciso con i valori numerici: non arrotondare se non necessario.
        """)
    @UserMessage("""
        Estrai i dati tecnici da questo testo grezzo relativo a un autoveicolo.
        Restituisci SOLO i dati esplicitamente presenti nel testo.
        Imposta null per i campi non trovati.

        TESTO GREZZO:
        {{rawText}}
        """)
    CarDataDTO extractCarData(@V("rawText") String rawText);
}

package com.santofem.redditoauto.ai;

import com.santofem.redditoauto.ai.dto.CarDataDTO;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AiService LangChain4j per la generazione DIRETTA di dati tecnici auto.
 *
 * QUANDO SI USA:
 * Quando il WebScraper non riesce a trovare dati (tutti i siti bloccano
 * il bot, errori SSL, testo troppo corto) questo service chiede direttamente
 * a Gemini di usare il suo training set per fornire i dati tecnici ufficiali.
 *
 * DIFFERENZA DA AiCarDataExtractor:
 * - AiCarDataExtractor: estrae dati DA un testo grezzo fornito
 * - AiDirectDataProvider: genera dati DA ZERO basandosi su marca/modello/anno/motore
 *
 * IMPORTANTE:
 * Gemini potrebbe avere dati imprecisi o non aggiornatissimi.
 * I record salvati da questo provider hanno confermato_manualmente=false
 * e fonte_dati=ai-direct per permettere revisione successiva.
 *
 * ANTI-ALLUCINAZIONE:
 * - temperature=0.0 (LangChain4jConfig)
 * - Il prompt chiede valori ufficiali WLTP/omologati, non stime
 * - Campi sconosciuti devono essere null
 */
public interface AiDirectDataProvider {

    @SystemMessage("""
        Sei un database tecnico di autoveicoli. Conosci le schede tecniche ufficiali
        di tutti i veicoli prodotti fino al tuo knowledge cutoff.
        
        Il tuo compito e' restituire i dati tecnici UFFICIALI e OMOLOGATI
        del veicolo specificato, esattamente come compaiono nella scheda tecnica
        del costruttore o nelle omologazioni europee.
        
        REGOLE:
        1. Usa SOLO dati ufficiali del costruttore o dati WLTP/NEDC omologati.
        2. Se non sei sicuro di un valore, metti null. MAI inventare o stimare.
        3. Per i consumi usa l/100km ciclo WLTP combinato.
        4. tipoCarburante: usa ESATTAMENTE uno di:
           BENZINA, DIESEL, GPL, METANO, IBRIDO_BENZINA, IBRIDO_DIESEL, IBRIDO_PLUGIN, ELETTRICO, IDROGENO
        5. tipoCambio: usa ESATTAMENTE uno di:
           MANUALE, AUTOMATICO_TRADIZIONALE, DCT, CVT, SINGOLA_MARCIA
        6. Prezzi: listino italiano ufficiale in euro (senza tasse accessorie).
        7. Restituisci SOLO il JSON strutturato, nessun testo aggiuntivo.
        """)
    @UserMessage("""
        Fornisci la scheda tecnica completa per il seguente veicolo:
        
        Marca: {marca}
        Modello: {modello}
        Motorizzazione: {motore}
        Anno: {anno}
        
        Popola tutti i campi che conosci con certezza.
        Imposta null per i campi che non conosci o di cui non sei sicuro.
        """)
    CarDataDTO getCarData(
        @V("marca") String marca,
        @V("modello") String modello,
        @V("motore") String motore,
        @V("anno") int anno
    );
}

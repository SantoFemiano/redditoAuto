package com.santofem.redditoauto.ai;

import com.santofem.redditoauto.ai.dto.CarDataDTO;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AiService LangChain4j per la generazione DIRETTA di dati tecnici auto.
 *
 * QUANDO SI USA:
 * Quando il WebScraper non riesce a trovare dati (bot-protection, errori SSL,
 * testi troppo corti) questo service chiede direttamente a Gemini di usare
 * il suo training set per fornire i dati tecnici ufficiali.
 *
 * NOTE SINTASSI LANGCHAIN4J:
 * - Template variables: DOPPIE graffe {{nomeVar}} — sintassi Mustache.
 *   Singola graffa {var} NON viene sostituita → Gemini riceve la stringa letterale.
 * - Parametri @V: devono essere String. I primitivi (int, double) non vengono
 *   interpolati correttamente — convertire con String.valueOf() nel chiamante.
 *
 * I record salvati tramite questo provider hanno:
 * - confermato_manualmente = false
 * - fonte_dati = "ai-direct:marca:modello:anno"
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
        
        Marca: {{marca}}
        Modello: {{modello}}
        Motorizzazione: {{motore}}
        Anno: {{anno}}
        
        Popola tutti i campi che conosci con certezza.
        Imposta null per i campi che non conosci o di cui non sei sicuro.
        """)
    CarDataDTO getCarData(
        @V("marca") String marca,
        @V("modello") String modello,
        @V("motore") String motore,
        @V("anno") String anno
    );
}

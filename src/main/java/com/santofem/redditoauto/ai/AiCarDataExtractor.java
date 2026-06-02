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
 * 1. Il WebScraperService recupera il testo grezzo (HTML pulito) da auto-data.net.
 * 2. Il testo viene passato insieme a marca/modello/motore/anno NOTI (dal frontend).
 * 3. LangChain4j costruisce il JSON Schema da CarDataDTO e lo invia a Gemini.
 * 4. Gemini risponde con un JSON strutturato.
 * 5. LangChain4j deserializza il JSON in un CarDataDTO fortemente tipizzato.
 *
 * PERCHE' INIETTARE MARCA/MODELLO/ANNO:
 * Il testo dello scraping contiene dati tecnici puri ma non sempre ripete
 * marca/modello come label esplicite. Iniettandoli come contesto obbligatorio
 * evitiamo che l'AI li lasci null causando isValid()=false.
 */
public interface AiCarDataExtractor {

    @SystemMessage("""
        Sei un sistema di estrazione dati specializzato in schede tecniche di autoveicoli.
        Il tuo unico compito e' leggere il testo grezzo fornito e mappare
        le informazioni nei campi del JSON di output.

        REGOLE FONDAMENTALI:
        1. I campi marca, modello, nomeMotore e annoProduzione ti vengono forniti
           ESPLICITAMENTE nel contesto: usali SEMPRE, non lasciarli mai null.
        2. Per tutti gli altri campi: estrai SOLO valori esplicitamente presenti nel testo.
        3. Se un'informazione non e' nel testo, il campo deve essere null.
        4. Per i consumi: usa l/100km. Se trovi km/l, converti: consumo = 100 / (km/l).
        5. Estrai sia kW che CV se entrambi presenti nel testo.
        6. Per i pneumatici usa il formato: '205/55 R16'.
        7. tipoCarburante: uno di BENZINA, DIESEL, GPL, METANO,
           IBRIDO_BENZINA, IBRIDO_DIESEL, IBRIDO_PLUGIN, ELETTRICO, IDROGENO.
        8. tipoCambio: uno di MANUALE, AUTOMATICO_TRADIZIONALE, DCT, CVT, SINGOLA_MARCIA.
        9. Prezzi in euro come numero decimale senza simboli (es. 32500.0).
        10. Restituisci SOLO il JSON strutturato, nessun testo aggiuntivo.
        """)
    @UserMessage("""
        CONTESTO VEICOLO (usa questi valori obbligatoriamente nei campi corrispondenti):
        - marca: {marca}
        - modello: {modello}
        - nomeMotore: {motore}
        - annoProduzione: {anno}

        Estrai dal testo grezzo qui sotto tutti gli altri dati tecnici disponibili
        (consumi, potenza kW/CV, cilindrata, pneumatici, cambio, carburante, ecc.).
        Imposta null per i campi non trovati nel testo. Non inventare nulla.

        TESTO GREZZO:
        {rawText}
        """)
    CarDataDTO extractCarData(
            @V("marca")   String marca,
            @V("modello") String modello,
            @V("motore")  String motore,
            @V("anno")    String anno,
            @V("rawText") String rawText);
}

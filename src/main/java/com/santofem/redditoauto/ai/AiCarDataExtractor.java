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
 * 2. Il testo viene passato a Gemini insieme al contesto veicolo.
 * 3. LangChain4j costruisce il JSON Schema da CarDataDTO e lo invia a Gemini.
 * 4. Gemini risponde con un JSON strutturato.
 * 5. LangChain4j deserializza il JSON in un CarDataDTO fortemente tipizzato.
 * 6. L'orchestratore sovrascrive SEMPRE i campi identitari (marca, modello,
 *    nomeMotore, annoProduzione) con i valori noti dal frontend.
 *
 * NOTA CRITICA SUI PLACEHOLDER LANGCHAIN4J:
 * In @UserMessage i placeholder per @V usano la sintassi {{nomeVar}} con DOPPIE
 * graffe. La singola graffa {nomeVar} NON viene sostituita e viene passata
 * verbatim a Gemini, che la restitutisce cosi' com'e' nel JSON output.
 * Riferimento: https://docs.langchain4j.dev/tutorials/ai-services#parameter-passing
 */
public interface AiCarDataExtractor {

    @SystemMessage("""
        Sei un sistema di estrazione dati specializzato in schede tecniche di autoveicoli.
        Il tuo unico compito e' leggere il TESTO GREZZO fornito ed estrarre
        i valori numerici e categorici nei campi JSON richiesti.

        REGOLE FONDAMENTALI:
        1. Estrai SOLO valori esplicitamente presenti nel testo grezzo.
        2. Se un'informazione non e' nel testo, il campo deve essere null.
        3. Per i consumi: usa l/100km. Se trovi km/l, converti: consumo = 100 / (km/l).
        4. Estrai sia kW che CV se entrambi presenti nel testo.
        5. Per i pneumatici usa il formato: '205/55 R16'.
        6. tipoCarburante: uno di BENZINA, DIESEL, GPL, METANO,
           IBRIDO_BENZINA, IBRIDO_DIESEL, IBRIDO_PLUGIN, ELETTRICO, IDROGENO.
        7. tipoCambio: uno di MANUALE, AUTOMATICO_TRADIZIONALE, DCT, CVT, SINGOLA_MARCIA.
        8. Prezzi in euro come numero decimale senza simboli (es. 32500.0).
        9. Restituisci SOLO il JSON strutturato, nessun testo aggiuntivo.
        10. Per i campi marca, modello, nomeMotore e annoProduzione: copia
            esattamente i valori indicati nel CONTESTO VEICOLO.
        """)
    @UserMessage("""
        CONTESTO VEICOLO (copia questi valori esatti nei campi corrispondenti del JSON):
        marca = "{{marcaInput}}"
        modello = "{{modelloInput}}"
        nomeMotore = "{{motoreInput}}"
        annoProduzione = {{annoInput}}

        Estrai dal TESTO GREZZO qui sotto tutti i dati tecnici disponibili
        (consumi, potenza kW/CV, cilindrata, pneumatici, cambio, carburante, ecc.).
        Imposta null per i campi non trovati nel testo. Non inventare nulla.

        TESTO GREZZO:
        {{rawText}}
        """)
    CarDataDTO extractCarData(
            @V("marcaInput")   String marca,
            @V("modelloInput") String modello,
            @V("motoreInput")  String motore,
            @V("annoInput")    String anno,
            @V("rawText")      String rawText);
}

package com.santofem.redditoauto.ai;

import com.santofem.redditoauto.ai.dto.CarDataDTO;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AiService LangChain4j per l'estrazione strutturata di dati tecnici auto.
 */
public interface AiCarDataExtractor {

    @SystemMessage("""
        Sei un sistema di estrazione dati specializzato in autoveicoli. 
        Riceverai un TESTO GREZZO (proveniente da auto-data.net) che contiene SOLO dati tecnici.

        REGOLE PER I DATI TECNICI (Motore, Dimensioni, Consumi):
        1. Estrai questi valori SOLO dal TESTO GREZZO fornito. Non inventarli.
        2. Per i consumi: usa l/100km. Se trovi km/l, converti: consumo = 100 / (km/l).
        3. Estrai sia kW che CV se entrambi presenti nel testo.
        4. tipoCarburante: uno di BENZINA, DIESEL, GPL, METANO, IBRIDO_BENZINA, IBRIDO_DIESEL, IBRIDO_PLUGIN, ELETTRICO, IDROGENO.
        5. tipoCambio: uno di MANUALE, AUTOMATICO_TRADIZIONALE, DCT, CVT, SINGOLA_MARCIA.
        
        REGOLE DI IDENTITA' (Marca, Modello, Motore, Anno):
        6. Per marca e modello: copia sempre esattamente i valori dal CONTESTO VEICOLO.
        7. Per il nomeMotore: se nel CONTESTO VEICOLO e' "sconosciuto", deduci il nome del motore dal TITOLO o dal TESTO GREZZO. Altrimenti usa il CONTESTO.
        8. Per l'annoProduzione: se nel CONTESTO VEICOLO e' 0, estrai l'anno di inizio produzione dal TESTO GREZZO. Altrimenti usa il CONTESTO.
        
        REGOLE PER I DATI COMMERCIALI E MANUTENZIONE (STIME DELL'AI):
        9. Poiche' il testo grezzo non contiene mai i prezzi e i costi di manutenzione, devi OBBLIGATORIAMENTE usare la tua conoscenza interna per stimare in modo realistico i seguenti campi per il mercato italiano:
           - prezzoListinoEur: il prezzo in euro dell'auto da NUOVA in quell'anno (senza simboli, es. 15500.0).
           - costoTagliandoBaseEur: stima del costo di un tagliando ordinario (olio/filtri).
           - costoTagliandoMaiorEur: stima di un tagliando maggiore (cinghia, freni, ecc.).
           - intervalloTagliandoKm: es. 15000, 20000 o 30000.
           - intervalloTagliandoMaiorKm: es. 60000, 90000 o 120000.
           - gruppoAssicurativo: una stima (da 1 a 20).
           NON restituire MAI 0 o null per questi campi, fai la tua migliore stima di mercato!
        
        REGOLE ANTI-ALLUCINAZIONE:
        10. Restituisci SOLO il JSON.
        11. Se nel testo ci sono varianti multiple, estrai solo la prima e ignora il resto.
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
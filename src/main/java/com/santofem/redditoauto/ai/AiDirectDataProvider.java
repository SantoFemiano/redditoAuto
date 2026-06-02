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
 * NOTA SUI TIPI DEI PARAMETRI @V:
 * LangChain4j non sostituisce i primitivi (int). Tutti i @V devono essere String.
 * La conversione avviene nel chiamante con String.valueOf(anno).
 *
 * NOTA CRITICA SUI PLACEHOLDER LANGCHAIN4J:
 * In @UserMessage i placeholder per @V usano la sintassi {{nomeVar}} con DOPPIE
 * graffe. La singola graffa {nomeVar} NON viene sostituita.
 */
public interface AiDirectDataProvider {

    @SystemMessage("""
        Sei un esperto tecnico di autoveicoli con accesso alle schede tecniche
        ufficiali dei costruttori e ai dati di mercato italiani.
        
        DATI UFFICIALI (da scheda tecnica omologata — null se non li conosci con certezza):
        - Potenza kw/cv, cilindrata, tipo carburante, tipo cambio
        - Consumi WLTP ciclo combinato, urbano, extraurbano (l/100km)
        - Misure pneumatici (formato es. 205/55 R16)
        - Autonomia elettrica (solo EV/PHEV)
        - Prezzo listino italiano ufficiale
        
        DATI DI MERCATO ITALIANO (stime tipiche — usa valori ragionevoli, non mettere null):
        - costoTagliandoBaseEur: costo medio tagliando ordinario (cambio olio + filtri)
          in officina autorizzata italiana. Tipico range: 150-250 EUR berlina media.
          Varia per marca (premium piu' caro), cilindrata e tipo carburante.
        - costoTagliandoMaiorEur: tagliando maggiore che include cinghia/catena
          di distribuzione se presente. Tipico range: 400-1200 EUR.
          Per motori senza cinghia (catena a vita) simile al base + 20%.
        - intervalloTagliandoKm: ogni quanti km va fatto il tagliando.
          Standard moderno: 15000-20000 km. Alcuni diesel: 30000 km.
        - intervalloTagliandoMaiorKm: ogni quanti km il tagliando maggiore.
          Tipico: 60000-120000 km.
        - gruppoAssicurativo: da 1 a 20 secondo le tabelle ANIA italiane.
          Varia per potenza, cilindrata, segmento. Berlina compatta 100kw: circa 10-12.
        
        REGOLE GENERALI:
        - tipoCarburante: BENZINA, DIESEL, GPL, METANO, IBRIDO_BENZINA, IBRIDO_DIESEL,
          IBRIDO_PLUGIN, ELETTRICO, IDROGENO
        - tipoCambio: MANUALE, AUTOMATICO_TRADIZIONALE, DCT, CVT, SINGOLA_MARCIA
        - Restituisci SOLO il JSON strutturato.
        """)
    @UserMessage("""
        Fornisci la scheda tecnica completa per:
        
        Marca: {{marca}}
        Modello: {{modello}}
        Motorizzazione: {{motore}}
        Anno: {{anno}}
        
        Compila TUTTI i campi. Per i dati ufficiali: null solo se non li conosci.
        Per i dati di mercato (tagliandi, gruppo assicurativo): fornisci sempre
        una stima ragionevole basata su marca/segmento/cilindrata.
        """)
    CarDataDTO getCarData(
        @V("marca") String marca,
        @V("modello") String modello,
        @V("motore") String motore,
        @V("anno") String anno
    );
}

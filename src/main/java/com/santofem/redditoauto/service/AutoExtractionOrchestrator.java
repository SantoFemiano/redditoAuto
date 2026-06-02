package com.santofem.redditoauto.service;

import com.santofem.redditoauto.ai.AiCarDataExtractor;
import com.santofem.redditoauto.ai.dto.CarDataDTO;
import com.santofem.redditoauto.controller.dto.MotorizzazioneResponseDTO;
import com.santofem.redditoauto.entity.Marca;
import com.santofem.redditoauto.entity.Modello;
import com.santofem.redditoauto.entity.Motorizzazione;
import com.santofem.redditoauto.mapper.CarDataMapper;
import com.santofem.redditoauto.repository.MarcaRepository;
import com.santofem.redditoauto.repository.ModelloRepository;
import com.santofem.redditoauto.repository.MotorizzazioneRepository;
import com.santofem.redditoauto.scraper.WebScraper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Orchestratore del flusso completo di estrazione dati auto:
 *
 *   [marca + modello + motore + anno]
 *        ↓
 *   WebScraper.scrape()     ← cerca il testo grezzo sul web
 *        ↓
 *   AiCarDataExtractor      ← Gemini mappa testo grezzo → CarDataDTO
 *        ↓
 *   deduplicazione DB       ← evita duplicati
 *        ↓
 *   CarDataMapper + save()  ← persiste Motorizzazione
 *        ↓
 *   MotorizzazioneResponseDTO
 *
 * Questo service e' anche il punto di ingresso per il CalcoloSostenibilitaService:
 * prima si assicura che i dati tecnici siano nel DB, poi il calcolo li legge.
 *
 * NOTA: inject WebScraper (interfaccia), non WebScraperService.
 * Questo permette di sostituire con un mock nei test senza Spring context.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoExtractionOrchestrator {

    private final WebScraper webScraper;
    private final AiCarDataExtractor aiExtractor;
    private final CarDataMapper carDataMapper;
    private final MarcaRepository marcaRepository;
    private final ModelloRepository modelloRepository;
    private final MotorizzazioneRepository motorizzazioneRepository;

    // -----------------------------------------------
    // ENTRY POINT PRINCIPALE — usato dal Controller
    // -----------------------------------------------

    /**
     * Flusso completo da parametri utente a DB.
     *
     * <p>Prima controlla se i dati sono gia' nel DB (cache DB).
     * Se si', evita scraping + AI call. Altrimenti esegue il flusso completo.
     *
     * @param marca   es. "Volkswagen"
     * @param modello es. "Golf"
     * @param motore  es. "2.0 TDI 150 CV DSG"
     * @param anno    es. 2022
     * @return DTO della motorizzazione salvata o gia' esistente
     * @throws IllegalStateException se lo scraping non trova dati o l'AI fallisce
     */
    @Transactional
    public MotorizzazioneResponseDTO estraiDaParametri(
            String marca, String modello, String motore, int anno) {

        log.info("[Orchestratore] Richiesta estrazione: {} {} {} {}",
            marca, modello, motore, anno);

        // --- Cache DB: verifica se esiste gia' ---
        List<Motorizzazione> esistenti = motorizzazioneRepository
            .findByMarcaModelloAnno(marca, modello, anno);

        if (!esistenti.isEmpty()) {
            // Cerca match esatto sul nome motore (case-insensitive)
            Optional<Motorizzazione> match = esistenti.stream()
                .filter(m -> motore.equalsIgnoreCase(m.getNomeMotore()))
                .findFirst();

            if (match.isPresent()) {
                log.info("[Orchestratore] Cache hit: motorizzazione id={} gia' presente",
                    match.get().getId());
                return carDataMapper.toResponseDTO(match.get());
            }
        }

        // --- Cache miss: scraping + AI ---
        log.info("[Orchestratore] Cache miss: avvio scraping per {} {} {}",
            marca, modello, motore);

        String testoGrezzo = webScraper.scrape(marca, modello, motore, anno)
            .orElseThrow(() -> new IllegalStateException(
                "Nessuna fonte web ha restituito dati per: "
                + marca + " " + modello + " " + motore + " (" + anno + ")."
                + " Prova a verificare marca/modello/anno o inserisci i dati manualmente."
            ));

        return estraiDaTesto(testoGrezzo, "scraping:" + marca + ":" + modello + ":" + anno);
    }

    // -----------------------------------------------
    // ENTRY POINT SECONDARIO — per URL diretti (admin/debug)
    // -----------------------------------------------

    /**
     * Flusso da URL diretto (es. link a una scheda tecnica specifica).
     * Utile per arricchire il DB manualmente o da job schedulati.
     *
     * @param url       URL della pagina da scrapare
     * @param fonteDati etichetta della fonte da salvare in DB
     */
    @Transactional
    public MotorizzazioneResponseDTO estraiDaUrl(String url, String fonteDati) {
        log.info("[Orchestratore] Estrazione da URL: {}", url);
        throw new UnsupportedOperationException(
            "estraiDaUrl() non ancora implementato. Usa estraiDaParametri() invece.");
    }

    // -----------------------------------------------
    // INNER — da testo grezzo a DB
    // -----------------------------------------------

    /**
     * Dato un testo grezzo gia' disponibile, esegue AI extraction + persist.
     * Usato internamente da estraiDaParametri() dopo lo scraping.
     */
    @Transactional
    public MotorizzazioneResponseDTO estraiDaTesto(String testoGrezzo, String fonteDati) {
        // Step 1: AI extraction
        CarDataDTO dto = aiExtractor.extractCarData(testoGrezzo);
        log.info("[AI] Estratto: {} {} {} ({})",
            dto.marca(), dto.modello(), dto.nomeMotore(), dto.annoProduzione());

        // Step 2: Validazione minima output AI
        if (!dto.isValid()) {
            throw new IllegalStateException(
                "L'AI non ha estratto i campi minimi obbligatori (marca, modello, potenzaKw, tipoCarburante). "
                + "Il testo sorgente potrebbe non contenere una scheda tecnica valida.");
        }

        // Step 3: Deduplicazione fine (marca+modello+anno)
        List<Motorizzazione> esistenti = motorizzazioneRepository
            .findByMarcaModelloAnno(dto.marca(), dto.modello(), dto.annoProduzione());

        if (!esistenti.isEmpty()) {
            log.info("[Orchestratore] Dedup: motorizzazione gia' presente (id={})",
                esistenti.get(0).getId());  // FIX: get(0) invece di getFirst() — compatibile Java 17
            return carDataMapper.toResponseDTO(esistenti.get(0));
        }

        // Step 4: Risolve/crea Marca (find-or-create)
        Marca marca = marcaRepository.findByNomeIgnoreCase(dto.marca())
            .orElseGet(() -> {
                log.info("[DB] Creazione nuova marca: {}", dto.marca());
                return marcaRepository.save(
                    Marca.builder().nome(capitalizza(dto.marca())).build());
            });

        // Step 5: Risolve/crea Modello (find-or-create)
        Modello modello = modelloRepository
            .findByMarcaIdAndNomeIgnoreCase(marca.getId(), dto.modello())
            .orElseGet(() -> {
                log.info("[DB] Creazione nuovo modello: {} {}", dto.marca(), dto.modello());
                return modelloRepository.save(
                    Modello.builder()
                        .marca(marca)
                        .nome(capitalizza(dto.modello()))
                        .annoInizio(dto.annoProduzione())
                        .build());
            });

        // Step 6: Mappa DTO → entita' e persisti
        Motorizzazione motorizzazione = carDataMapper.toEntity(dto, modello);
        motorizzazione.setFonteDati(fonteDati);
        Motorizzazione salvata = motorizzazioneRepository.save(motorizzazione);

        log.info("[DB] Nuova motorizzazione salvata con id={}", salvata.getId());
        return carDataMapper.toResponseDTO(salvata);
    }

    // -----------------------------------------------
    // UTILITY
    // -----------------------------------------------

    /** Capitalizza correttamente: "volkswagen" → "Volkswagen" */
    private String capitalizza(String nome) {
        if (nome == null || nome.isBlank()) return nome;
        String t = nome.trim();
        return Character.toUpperCase(t.charAt(0)) + t.substring(1).toLowerCase();
    }
}

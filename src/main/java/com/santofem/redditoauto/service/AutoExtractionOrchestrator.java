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
 *   isValid()               ← blocca se AI non ha estratto i minimi
 *        ↓
 *   deduplicazione DB       ← evita duplicati
 *        ↓
 *   CarDataMapper + save()  ← persiste Motorizzazione
 *        ↓
 *   MotorizzazioneResponseDTO
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

    @Transactional
    public MotorizzazioneResponseDTO estraiDaParametri(
            String marca, String modello, String motore, int anno) {

        log.info("[Orchestratore] Richiesta estrazione: {} {} {} {}",
            marca, modello, motore, anno);

        // --- Cache DB ---
        List<Motorizzazione> esistenti = motorizzazioneRepository
            .findByMarcaModelloAnno(marca, modello, anno);

        if (!esistenti.isEmpty()) {
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
                "Nessuna fonte web ha restituito dati tecnici per: "
                + marca + " " + modello + " " + motore + " (" + anno + ")."
                + " Prova a verificare marca/modello/anno o inserisci i dati manualmente."
            ));

        return estraiDaTesto(testoGrezzo, "scraping:" + marca + ":" + modello + ":" + anno);
    }

    // -----------------------------------------------
    // ENTRY POINT SECONDARIO — per URL diretti (admin/debug)
    // -----------------------------------------------

    @Transactional
    public MotorizzazioneResponseDTO estraiDaUrl(String url, String fonteDati) {
        log.info("[Orchestratore] Estrazione da URL: {}", url);
        throw new UnsupportedOperationException(
            "estraiDaUrl() non ancora implementato. Usa estraiDaParametri() invece.");
    }

    // -----------------------------------------------
    // INNER — da testo grezzo a DB
    // -----------------------------------------------

    @Transactional
    public MotorizzazioneResponseDTO estraiDaTesto(String testoGrezzo, String fonteDati) {
        // Step 1: AI extraction
        CarDataDTO dto = aiExtractor.extractCarData(testoGrezzo);
        log.info("[AI] Estratto: marca='{}' modello='{}' motore='{}' anno={} carburante='{}'",
            dto.marca(), dto.modello(), dto.nomeMotore(), dto.annoProduzione(), dto.tipoCarburante());

        // Step 2: Validazione minima — BLOCCA se l'AI non ha estratto i dati fondamentali.
        // NOTA: isValid() difende anche contro la stringa letterale "null" che Gemini
        // puo' restituire al posto di un vero null Java quando il testo sorgente
        // non contiene una scheda tecnica valida (es. listing annunci di AutoScout24).
        if (!dto.isValid()) {
            log.warn("[AI] Dati insufficienti — salvataggio bloccato. " +
                     "DTO ricevuto: marca='{}' modello='{}' motore='{}' kw={} carburante='{}'. " +
                     "Probabile causa: il testo sorgente non contiene una scheda tecnica strutturata.",
                dto.marca(), dto.modello(), dto.nomeMotore(), dto.potenzaKw(), dto.tipoCarburante());
            throw new IllegalStateException(
                "L'AI non ha estratto i campi minimi obbligatori (marca, modello, nomeMotore, potenzaKw, tipoCarburante). " +
                "Il testo sorgente potrebbe non contenere una scheda tecnica valida. " +
                "Prova con marca/modello/anno diversi o inserisci i dati manualmente."
            );
        }

        // Step 3: Deduplicazione
        List<Motorizzazione> esistenti = motorizzazioneRepository
            .findByMarcaModelloAnno(dto.marca(), dto.modello(), dto.annoProduzione());

        if (!esistenti.isEmpty()) {
            log.info("[Orchestratore] Dedup: motorizzazione gia' presente (id={})",
                esistenti.get(0).getId());
            return carDataMapper.toResponseDTO(esistenti.get(0));
        }

        // Step 4: Risolve/crea Marca
        Marca marca = marcaRepository.findByNomeIgnoreCase(dto.marca())
            .orElseGet(() -> {
                log.info("[DB] Creazione nuova marca: {}", dto.marca());
                return marcaRepository.save(
                    Marca.builder().nome(capitalizza(dto.marca())).build());
            });

        // Step 5: Risolve/crea Modello
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

        // Step 6: Mappa + persisti
        Motorizzazione motorizzazione = carDataMapper.toEntity(dto, modello);
        motorizzazione.setFonteDati(fonteDati);
        Motorizzazione salvata = motorizzazioneRepository.save(motorizzazione);

        log.info("[DB] Nuova motorizzazione salvata con id={}", salvata.getId());
        return carDataMapper.toResponseDTO(salvata);
    }

    // -----------------------------------------------
    // UTILITY
    // -----------------------------------------------

    private String capitalizza(String nome) {
        if (nome == null || nome.isBlank()) return nome;
        String t = nome.trim();
        return Character.toUpperCase(t.charAt(0)) + t.substring(1).toLowerCase();
    }
}

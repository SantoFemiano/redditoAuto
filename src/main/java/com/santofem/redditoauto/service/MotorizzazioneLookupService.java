package com.santofem.redditoauto.service;

import com.santofem.redditoauto.entity.Motorizzazione;
import com.santofem.redditoauto.repository.MotorizzazioneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Responsabile della cache/dedup DB.
 * Isola la logica di ricerca motorizzazione esistente
 * dal resto dell'orchestrazione.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MotorizzazioneLookupService {

    private final MotorizzazioneRepository motorizzazioneRepository;

    /**
     * Cerca una motorizzazione corrispondente con match esatto o parziale sul nome motore.
     * Restituisce empty se non trovata.
     */
    public Optional<Motorizzazione> findExisting(String marca, String modello, String motore, int anno) {
        List<Motorizzazione> esistenti = motorizzazioneRepository.findByMarcaModelloAnno(marca, modello, anno);

        if (esistenti.isEmpty()) return Optional.empty();

        // Match esatto
        Optional<Motorizzazione> exactMatch = esistenti.stream()
            .filter(m -> motore.equalsIgnoreCase(m.getNomeMotore()))
            .findFirst();
        if (exactMatch.isPresent()) {
            log.info("[Lookup] Cache hit esatto: id={}", exactMatch.get().getId());
            return exactMatch;
        }

        // Match parziale
        Optional<Motorizzazione> partialMatch = esistenti.stream()
            .filter(m -> m.getNomeMotore() != null
                && !isPlaceholder(m.getNomeMotore())
                && (motore.toLowerCase().contains(m.getNomeMotore().toLowerCase())
                    || m.getNomeMotore().toLowerCase().contains(motore.toLowerCase())))
            .findFirst();
        if (partialMatch.isPresent()) {
            log.info("[Lookup] Cache hit parziale: id={} nome='{}'",
                partialMatch.get().getId(), partialMatch.get().getNomeMotore());
        }
        return partialMatch;
    }

    /**
     * Controlla se una motorizzazione è già presente per deduplicazione post-AI.
     */
    public Optional<Motorizzazione> findDuplicate(String marca, String modello, String motore, int anno) {
        List<Motorizzazione> esistenti = motorizzazioneRepository.findByMarcaModelloAnno(marca, modello, anno);
        return esistenti.stream()
            .filter(m -> m.getNomeMotore() != null
                && !isPlaceholder(m.getNomeMotore())
                && (motore.equalsIgnoreCase(m.getNomeMotore())
                    || motore.toLowerCase().contains(m.getNomeMotore().toLowerCase())
                    || m.getNomeMotore().toLowerCase().contains(motore.toLowerCase())))
            .findFirst();
    }

    private boolean isPlaceholder(String value) {
        if (value == null || value.isBlank()) return true;
        String t = value.trim();
        return t.equalsIgnoreCase("null") || (t.startsWith("{") && t.endsWith("}"));
    }
}

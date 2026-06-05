package com.santofem.redditoauto.service;

import com.santofem.redditoauto.ai.dto.CarDataDTO;
import com.santofem.redditoauto.controller.dto.MotorizzazioneResponseDTO;
import com.santofem.redditoauto.entity.Marca;
import com.santofem.redditoauto.entity.Modello;
import com.santofem.redditoauto.entity.Motorizzazione;
import com.santofem.redditoauto.mapper.CarDataMapper;
import com.santofem.redditoauto.repository.MarcaRepository;
import com.santofem.redditoauto.repository.ModelloRepository;
import com.santofem.redditoauto.repository.MotorizzazioneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;

/**
 * Responsabile della persistenza Marca → Modello → Motorizzazione.
 * Isola tutta la logica JPA dall'orchestrazione.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarDataPersistenceService {

    private final MarcaRepository marcaRepository;
    private final ModelloRepository modelloRepository;
    private final MotorizzazioneRepository motorizzazioneRepository;
    private final CarDataMapper carDataMapper;
    private final MotorizzazioneLookupService lookupService;

    /**
     * Persiste il DTO nel DB, con dedup preventiva.
     * Se esiste già una motorizzazione equivalente, restituisce quella.
     */
    @Transactional
    public MotorizzazioneResponseDTO saveOrReturn(CarDataDTO dto, String fonteDati) {
        if (!dto.isValid()) {
            log.warn("[Persistence] DTO non valido, salvataggio bloccato: marca='{}' modello='{}' kw={}",
                dto.marca(), dto.modello(), dto.potenzaKw());
            throw new IllegalStateException(
                "L'AI non ha estratto i campi minimi obbligatori. "
                + "Controlla che marca/modello/anno siano corretti.");
        }

        // Dedup post-AI
        Optional<Motorizzazione> dup = lookupService.findDuplicate(
            dto.marca(), dto.modello(), dto.nomeMotore(), dto.annoProduzione());
        if (dup.isPresent()) {
            log.info("[Persistence] Dedup: restituisco motorizzazione esistente id={}", dup.get().getId());
            return carDataMapper.toResponseDTO(dup.get());
        }

        Marca marca = marcaRepository.findByNomeIgnoreCase(dto.marca())
            .orElseGet(() -> {
                log.info("[Persistence] Nuova marca: {}", dto.marca());
                return marcaRepository.save(
                    Marca.builder().nome(capitalizza(dto.marca())).build());
            });

        Modello modello = modelloRepository
            .findByMarcaIdAndNomeIgnoreCase(marca.getId(), dto.modello())
            .orElseGet(() -> {
                log.info("[Persistence] Nuovo modello: {} {}", dto.marca(), dto.modello());
                return modelloRepository.save(
                    Modello.builder()
                        .marca(marca)
                        .nome(capitalizza(dto.modello()))
                        .annoInizio(dto.annoProduzione())
                        .build());
            });

        Motorizzazione motorizzazione = carDataMapper.toEntity(dto, modello);
        motorizzazione.setFonteDati(fonteDati);
        Motorizzazione salvata = motorizzazioneRepository.save(motorizzazione);

        log.info("[Persistence] Salvata id={} (fonte: {})", salvata.getId(), fonteDati);
        return carDataMapper.toResponseDTO(salvata);
    }

    private String capitalizza(String nome) {
        if (nome == null || nome.isBlank()) return nome;
        String t = nome.trim();
        return Character.toUpperCase(t.charAt(0)) + t.substring(1).toLowerCase();
    }
}

package com.santofem.redditoauto.service;

import com.santofem.redditoauto.controller.dto.MotorizzazioneResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @deprecated Rimpiazzato da {@link AutoExtractionFacade}.
 * Mantenuto per retrocompatibilità: delega tutto alla facade.
 * Rimuovere quando il controller sarà aggiornato a usare AutoExtractionFacade direttamente.
 */
@Deprecated(since = "2.0", forRemoval = true)
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoExtractionOrchestrator {

    private final AutoExtractionFacade facade;

    @Transactional
    public MotorizzazioneResponseDTO estraiDaParametri(
            String marca, String modello, String motore, int anno,
            int potenzaCv, String tipoCarburante, String tipoCambio) {
        return facade.estraiDaParametri(marca, modello, motore, anno, potenzaCv, tipoCarburante, tipoCambio);
    }

    @Transactional
    public MotorizzazioneResponseDTO estraiDaParametri(
            String marca, String modello, String motore, int anno) {
        return facade.estraiDaParametri(marca, modello, motore, anno);
    }

    @Transactional
    public MotorizzazioneResponseDTO estraiDaUrl(String url, String fonteDati) {
        return facade.estraiDaUrl(url, fonteDati);
    }

    @Transactional
    public MotorizzazioneResponseDTO estraiDaTesto(String testoGrezzo, String fonteDati) {
        return facade.estraiDaTesto(testoGrezzo, fonteDati);
    }
}

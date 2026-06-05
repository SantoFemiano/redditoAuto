package com.santofem.redditoauto.mapper;

import com.santofem.redditoauto.controller.dto.MotorizzazioneResponseDTO;
import com.santofem.redditoauto.entity.Motorizzazione;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper per la conversione type-safe Motorizzazione ↔ DTO.
 * Generazione automatica dell'implementazione a compile-time.
 * Nessun reflection a runtime, zero overhead.
 */
@Mapper(componentModel = "spring")
public interface MotorizzazioneMapper {

    @Mapping(source = "modello.nome", target = "nomeModello")
    @Mapping(source = "modello.marca.nome", target = "nomeMarca")
    MotorizzazioneResponseDTO toResponse(Motorizzazione entity);
}

package com.santofem.redditoauto.mapper;

import com.santofem.redditoauto.controller.dto.MotorizzazioneResponseDTO;
import com.santofem.redditoauto.entity.Motorizzazione;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Mapper MapStruct per la conversione tra Motorizzazione entity e DTO.
 *
 * componentModel="spring" → generato come @Component Spring, iniettabile via @Autowired/@RequiredArgsConstructor.
 * unmappedTargetPolicy=ERROR → il build fallisce se un campo del DTO non è mappato esplicitamente.
 * @BeanMapping(ignoreByDefault=true) → solo i campi con @Mapping esplicito vengono mappati, zero sorprese.
 *
 * Il mapper viene generato a compile-time da MapStruct 1.6.3 — nessuna reflection a runtime.
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface MotorizzazioneMapper {

    // ================================================================
    // Entity → MotorizzazioneResponseDTO (record Java 25)
    // ================================================================

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id",                   source = "id")
    @Mapping(target = "nomeMarca",             source = "modello.marca.nome")
    @Mapping(target = "nomeModello",           source = "modello.nome")
    @Mapping(target = "nomeMotore",            source = "nomeMotore")
    @Mapping(target = "annoProduzione",        source = "annoProduzione")
    @Mapping(target = "potenzaKw",             source = "potenzaKw")
    @Mapping(target = "potenzaCv",             source = "potenzaCv")
    @Mapping(target = "tipoCarburante",        source = "tipoCarburante")
    @Mapping(target = "tipoCambio",            source = "tipoCambio")
    @Mapping(target = "consumoMedioLitri100km",source = "consumoMedioLitri100km")
    @Mapping(target = "prezzoListinoEur",      source = "prezzoListinoEur")
    @Mapping(target = "runFlat",               source = "runFlat")
    @Mapping(target = "gruppoAssicurativo",    source = "gruppoAssicurativo")
    MotorizzazioneResponseDTO toResponseDTO(Motorizzazione entity);

    /** Mappa una lista di entity in una lista di DTO. */
    List<MotorizzazioneResponseDTO> toResponseDTOList(List<Motorizzazione> entities);

    // ================================================================
    // Motorizzazione → Motorizzazione (patch/update parziale)
    // Aggiorna solo i campi tecnici; non tocca id, modello, audit fields.
    // ================================================================

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "nomeMotore",                     source = "nomeMotore")
    @Mapping(target = "annoProduzione",                  source = "annoProduzione")
    @Mapping(target = "tipoCarburante",                  source = "tipoCarburante")
    @Mapping(target = "tipoCambio",                      source = "tipoCambio")
    @Mapping(target = "potenzaKw",                       source = "potenzaKw")
    @Mapping(target = "potenzaCv",                       source = "potenzaCv")
    @Mapping(target = "cilindrataCC",                    source = "cilindrataCC")
    @Mapping(target = "consumoMedioLitri100km",          source = "consumoMedioLitri100km")
    @Mapping(target = "consumoUrbanoLitri100km",         source = "consumoUrbanoLitri100km")
    @Mapping(target = "consumoExtraurbanoLitri100km",    source = "consumoExtraurbanoLitri100km")
    @Mapping(target = "autonomiaKmElettrica",            source = "autonomiaKmElettrica")
    @Mapping(target = "misuraPneumaticiAnteriori",       source = "misuraPneumaticiAnteriori")
    @Mapping(target = "misuraPneumaticiPosteriori",      source = "misuraPneumaticiPosteriori")
    @Mapping(target = "runFlat",                         source = "runFlat")
    @Mapping(target = "prezzoListinoEur",                source = "prezzoListinoEur")
    @Mapping(target = "costoTagliandoBaseEur",           source = "costoTagliandoBaseEur")
    @Mapping(target = "costoTagliandoMaiorEur",          source = "costoTagliandoMaiorEur")
    @Mapping(target = "intervalloTagliandoKm",           source = "intervalloTagliandoKm")
    @Mapping(target = "intervalloTagliandoMaiorKm",      source = "intervalloTagliandoMaiorKm")
    @Mapping(target = "gruppoAssicurativo",              source = "gruppoAssicurativo")
    @Mapping(target = "fonteDati",                       source = "fonteDati")
    @Mapping(target = "dataEstrazione",                  source = "dataEstrazione")
    @Mapping(target = "confermatoManualmente",           source = "confermatoManualmente")
    void updateEntityFromSource(Motorizzazione source, @MappingTarget Motorizzazione target);
}

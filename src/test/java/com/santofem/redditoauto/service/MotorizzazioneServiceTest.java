package com.santofem.redditoauto.service;

import com.santofem.redditoauto.controller.dto.MotorizzazioneResponseDTO;
import com.santofem.redditoauto.entity.Marca;
import com.santofem.redditoauto.entity.Modello;
import com.santofem.redditoauto.entity.Motorizzazione;
import com.santofem.redditoauto.entity.enums.TipoCarburante;
import com.santofem.redditoauto.mapper.CarDataMapper;
import com.santofem.redditoauto.repository.MotorizzazioneRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MotorizzazioneService — Unit Tests")
class MotorizzazioneServiceTest {

    @Mock private MotorizzazioneRepository motorizzazioneRepository;
    @Mock private CarDataMapper carDataMapper;

    @InjectMocks
    private MotorizzazioneService service;

    private Motorizzazione golfTdi;
    private MotorizzazioneResponseDTO golfTdiDTO;

    @BeforeEach
    void setUp() {
        Marca vw = Marca.builder().id(1L).nome("Volkswagen").build();
        Modello golf = Modello.builder().id(1L).marca(vw)
            .nome("Golf").annoInizio(2020).build();

        golfTdi = Motorizzazione.builder()
            .id(10L).modello(golf)
            .nomeMotore("2.0 TDI 150 CV").annoProduzione(2022)
            .tipoCarburante(TipoCarburante.DIESEL)
            .potenzaKw(110).potenzaCv(150)
            .consumoMedioLitri100km(new BigDecimal("5.8"))
            .runFlat(false).confermatoManualmente(false)
            .build();

        golfTdiDTO = MotorizzazioneResponseDTO.builder()
            .id(10L).marca("Volkswagen").modello("Golf")
            .nomeMotore("2.0 TDI 150 CV").annoProduzioneM(2022)
            .potenzaKw(110).tipoCarburante("DIESEL")
            .build();
    }

    @Test
    @DisplayName("findById — trovata: restituisce DTO")
    void findById_found_returnsDTO() {
        when(motorizzazioneRepository.findById(10L)).thenReturn(Optional.of(golfTdi));
        when(carDataMapper.toResponseDTO(golfTdi)).thenReturn(golfTdiDTO);

        MotorizzazioneResponseDTO result = service.findById(10L);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getMarca()).isEqualTo("Volkswagen");
    }

    @Test
    @DisplayName("findById — non trovata: lancia EntityNotFoundException")
    void findById_notFound_throwsEntityNotFoundException() {
        when(motorizzazioneRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("99");
    }

    @Test
    @DisplayName("search — con tutti i parametri: delega al repository")
    void search_withAllParams_delegatesToRepository() {
        when(motorizzazioneRepository
            .findByMarcaModelloAnno("Volkswagen", "Golf", 2022))
            .thenReturn(List.of(golfTdi));
        when(carDataMapper.toResponseDTO(golfTdi)).thenReturn(golfTdiDTO);

        List<MotorizzazioneResponseDTO> results =
            service.search("Volkswagen", "Golf", 2022);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getNomeMotore()).isEqualTo("2.0 TDI 150 CV");
    }

    @Test
    @DisplayName("search — parametri null: restituisce tutti")
    void search_withNullParams_returnsAll() {
        when(motorizzazioneRepository.findAll()).thenReturn(List.of(golfTdi));
        when(carDataMapper.toResponseDTO(golfTdi)).thenReturn(golfTdiDTO);

        List<MotorizzazioneResponseDTO> results = service.search(null, null, null);
        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("autocomplete — query troppo corta: restituisce lista vuota")
    void autocomplete_shortQuery_returnsEmpty() {
        assertThat(service.autocomplete("g")).isEmpty();
        assertThat(service.autocomplete(null)).isEmpty();
        assertThat(service.autocomplete("")).isEmpty();
        verifyNoInteractions(motorizzazioneRepository);
    }

    @Test
    @DisplayName("autocomplete — query valida: filtra correttamente")
    void autocomplete_validQuery_returnsMatches() {
        when(motorizzazioneRepository.findAll()).thenReturn(List.of(golfTdi));

        List<String> suggestions = service.autocomplete("golf");

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.getFirst()).contains("Golf");
        assertThat(suggestions.getFirst()).contains("Volkswagen");
    }

    @Test
    @DisplayName("autocomplete — query senza match: lista vuota")
    void autocomplete_noMatch_returnsEmpty() {
        when(motorizzazioneRepository.findAll()).thenReturn(List.of(golfTdi));

        List<String> suggestions = service.autocomplete("ferrari");
        assertThat(suggestions).isEmpty();
    }

    @Test
    @DisplayName("delete — id trovato: elimina correttamente")
    void delete_found_deletesSuccessfully() {
        when(motorizzazioneRepository.existsById(10L)).thenReturn(true);

        assertThatNoException().isThrownBy(() -> service.delete(10L));
        verify(motorizzazioneRepository).deleteById(10L);
    }

    @Test
    @DisplayName("delete — id non trovato: lancia EntityNotFoundException")
    void delete_notFound_throwsEntityNotFoundException() {
        when(motorizzazioneRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(99L))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("99");

        verify(motorizzazioneRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("confermaManualmente — setta flag e salva")
    void confermaManualment_setsFlag() {
        when(motorizzazioneRepository.findById(10L)).thenReturn(Optional.of(golfTdi));
        when(motorizzazioneRepository.save(golfTdi)).thenReturn(golfTdi);
        when(carDataMapper.toResponseDTO(golfTdi)).thenReturn(golfTdiDTO);

        service.confermaManualmente(10L);

        assertThat(golfTdi.getConfermatoManualmente()).isTrue();
        verify(motorizzazioneRepository).save(golfTdi);
    }
}

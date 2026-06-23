package com.santofem.redditoauto.service;

import com.santofem.redditoauto.ai.AiCarDataExtractor;
import com.santofem.redditoauto.ai.dto.CarDataDTO;
import com.santofem.redditoauto.controller.dto.MotorizzazioneResponseDTO;
import com.santofem.redditoauto.entity.Marca;
import com.santofem.redditoauto.entity.Modello;
import com.santofem.redditoauto.entity.Motorizzazione;
import com.santofem.redditoauto.entity.enums.TipoCarburante;
import com.santofem.redditoauto.mapper.CarDataMapper;
import com.santofem.redditoauto.repository.MarcaRepository;
import com.santofem.redditoauto.repository.ModelloRepository;
import com.santofem.redditoauto.repository.MotorizzazioneRepository;
import com.santofem.redditoauto.scraper.WebScraper;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AutoExtractionOrchestrator — Unit Tests")
class AutoExtractionOrchestratorTest {

    @Mock private WebScraper webScraper;
    @Mock private AiCarDataExtractor aiExtractor;
    @Mock private CarDataMapper carDataMapper;
    @Mock private MarcaRepository marcaRepository;
    @Mock private ModelloRepository modelloRepository;
    @Mock private MotorizzazioneRepository motorizzazioneRepository;

    @InjectMocks
    private AutoExtractionOrchestrator orchestrator;

    // --- Fixtures ---
    private static final String MARCA   = "Volkswagen";
    private static final String MODELLO = "Golf";
    private static final String MOTORE  = "2.0 TDI 150 CV";
    private static final int    ANNO    = 2022;

    private CarDataDTO validDto;
    private Marca      marcaEntity;
    private Modello    modelloEntity;
    private Motorizzazione motorizzazioneEntity;
    private MotorizzazioneResponseDTO responseDTO;

    @BeforeEach
    void setUp() {
        validDto = new CarDataDTO(
            MARCA, MODELLO, MOTORE, ANNO,
            "DIESEL", "DCT",
            110, 150, 1968,
            5.8, null, null, null,
            "205/55 R16", null, false,
            35000.0, 250.0, 700.0, 15000, 60000, 8
        );

        marcaEntity = Marca.builder().id(1L).nome(MARCA).build();
        modelloEntity = Modello.builder().id(1L).marca(marcaEntity)
            .nome(MODELLO).annoInizio(ANNO).build();
        motorizzazioneEntity = Motorizzazione.builder()
            .id(10L)
            .modello(modelloEntity)
            .nomeMotore(MOTORE)
            .annoProduzione(ANNO)
            .tipoCarburante(TipoCarburante.DIESEL)
            .potenzaKw(110)
            .potenzaCv(150)
            .consumoMedioLitri100km(new BigDecimal("5.8"))
            .runFlat(false)
            .confermatoManualmente(false)
            .build();

        responseDTO = MotorizzazioneResponseDTO.builder()
            .id(10L).marca(MARCA).modello(MODELLO)
            .nomeMotore(MOTORE).annoProduzioneM(ANNO)
            .build();
    }

    // -----------------------------------------------
    // estraiDaParametri — cache DB
    // -----------------------------------------------

    @Test
    @DisplayName("estraiDaParametri — cache hit: dati gia' presenti, no scraping")
    void estraiDaParametri_cacheHit_noScraping() {
        when(motorizzazioneRepository.findByMarcaModelloAnno(MARCA, MODELLO, ANNO))
            .thenReturn(List.of(motorizzazioneEntity));
        when(carDataMapper.toResponseDTO(motorizzazioneEntity))
            .thenReturn(responseDTO);

        MotorizzazioneResponseDTO result =
            orchestrator.estraiDaParametri(MARCA, MODELLO, MOTORE, ANNO);

        assertThat(result.getId()).isEqualTo(10L);
        // Scraping e AI non devono essere stati chiamati
        verifyNoInteractions(webScraper, aiExtractor);
    }

    // -----------------------------------------------
    // estraiDaTesto — flusso completo
    // -----------------------------------------------

    @Test
    @DisplayName("estraiDaTesto — happy path: scraping + AI + DB save")
    void estraiDaTesto_happyPath_savesAndReturns() {
        // Nessun record esistente
        when(motorizzazioneRepository.findByMarcaModelloAnno(anyString(), anyString(), any()))
            .thenReturn(List.of());

        when(aiExtractor.extractCarData(anyString())).thenReturn(validDto);

        when(marcaRepository.findByNomeIgnoreCase(MARCA))
            .thenReturn(Optional.of(marcaEntity));
        when(modelloRepository.findByMarcaIdAndNomeIgnoreCase(1L, MODELLO))
            .thenReturn(Optional.of(modelloEntity));

        when(carDataMapper.toEntity(validDto, modelloEntity))
            .thenReturn(motorizzazioneEntity);
        when(motorizzazioneRepository.save(any(Motorizzazione.class)))
            .thenReturn(motorizzazioneEntity);
        when(carDataMapper.toResponseDTO(motorizzazioneEntity))
            .thenReturn(responseDTO);

        MotorizzazioneResponseDTO result =
            orchestrator.estraiDaTesto("testo grezzo simulato", "test-fonte");

        assertThat(result.getId()).isEqualTo(10L);
        verify(motorizzazioneRepository).save(any(Motorizzazione.class));
    }

    @Test
    @DisplayName("estraiDaTesto — dedup: record gia' presente, non salva di nuovo")
    void estraiDaTesto_dedup_returnsExistingWithoutSave() {
        when(aiExtractor.extractCarData(anyString())).thenReturn(validDto);
        when(motorizzazioneRepository.findByMarcaModelloAnno(anyString(), anyString(), any()))
            .thenReturn(List.of(motorizzazioneEntity));
        when(carDataMapper.toResponseDTO(motorizzazioneEntity))
            .thenReturn(responseDTO);

        orchestrator.estraiDaTesto("qualunque testo", "fonte");

        // save() NON deve essere chiamato
        verify(motorizzazioneRepository, never()).save(any());
    }

    @Test
    @DisplayName("estraiDaTesto — AI output non valido: lancia IllegalStateException")
    void estraiDaTesto_invalidAiOutput_throwsException() {
        // DTO senza marca (isValid() = false)
        CarDataDTO invalidDto = new CarDataDTO(
            null, "Golf", "2.0 TDI", 2022,
            "DIESEL", null, 110, null, null,
            null, null, null, null, null, null,
            null, null, null, null, null, null, null
        );
        when(aiExtractor.extractCarData(anyString())).thenReturn(invalidDto);
        when(motorizzazioneRepository.findByMarcaModelloAnno(any(), any(), any()))
            .thenReturn(List.of());

        assertThatThrownBy(() -> orchestrator.estraiDaTesto("testo", "fonte"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("campi minimi obbligatori");
    }

    @Test
    @DisplayName("estraiDaParametri — scraper vuoto: lancia IllegalStateException")
    void estraiDaParametri_scraperEmpty_throwsException() {
        when(motorizzazioneRepository.findByMarcaModelloAnno(MARCA, MODELLO, ANNO))
            .thenReturn(List.of());
        when(webScraper.scrape(MARCA, MODELLO, MOTORE, ANNO))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            orchestrator.estraiDaParametri(MARCA, MODELLO, MOTORE, ANNO))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Nessuna fonte web");
    }

    @Test
    @DisplayName("estraiDaTesto — marca non presente in DB: viene creata (find-or-create)")
    void estraiDaTesto_marcaNotFound_createsNewMarca() {
        when(motorizzazioneRepository.findByMarcaModelloAnno(anyString(), anyString(), any()))
            .thenReturn(List.of());
        when(aiExtractor.extractCarData(anyString())).thenReturn(validDto);

        // Marca non trovata -> save
        when(marcaRepository.findByNomeIgnoreCase(MARCA)).thenReturn(Optional.empty());
        when(marcaRepository.save(any(Marca.class))).thenReturn(marcaEntity);

        when(modelloRepository.findByMarcaIdAndNomeIgnoreCase(1L, MODELLO))
            .thenReturn(Optional.of(modelloEntity));
        when(carDataMapper.toEntity(validDto, modelloEntity))
            .thenReturn(motorizzazioneEntity);
        when(motorizzazioneRepository.save(any())).thenReturn(motorizzazioneEntity);
        when(carDataMapper.toResponseDTO(motorizzazioneEntity)).thenReturn(responseDTO);

        orchestrator.estraiDaTesto("testo", "fonte");

        // Deve aver chiamato save() per la nuova Marca
        verify(marcaRepository).save(any(Marca.class));
    }
}

package com.santofem.redditoauto.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.santofem.redditoauto.entity.Marca;
import com.santofem.redditoauto.entity.Modello;
import com.santofem.redditoauto.entity.Motorizzazione;
import com.santofem.redditoauto.entity.enums.TipoCarburante;
import com.santofem.redditoauto.entity.enums.TipoCambio;
import com.santofem.redditoauto.repository.MarcaRepository;
import com.santofem.redditoauto.repository.ModelloRepository;
import com.santofem.redditoauto.repository.MotorizzazioneRepository;
import com.santofem.redditoauto.service.dto.CalcoloRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("CalcoloController — Integration Tests")
class CalcoloControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired MarcaRepository marcaRepository;
    @Autowired ModelloRepository modelloRepository;
    @Autowired MotorizzazioneRepository motorizzazioneRepository;

    private Long motorizzazioneId;

    @BeforeEach
    void setUp() {
        motorizzazioneRepository.deleteAll();
        modelloRepository.deleteAll();
        marcaRepository.deleteAll();

        Marca vw = marcaRepository.save(Marca.builder().nome("Volkswagen").build());
        Modello golf = modelloRepository.save(
            Modello.builder().marca(vw).nome("Golf").annoInizio(2020).build());

        Motorizzazione m = motorizzazioneRepository.save(Motorizzazione.builder()
            .modello(golf)
            .nomeMotore("2.0 TDI 150 CV DSG")
            .annoProduzione(2022)
            .tipoCarburante(TipoCarburante.DIESEL)
            .tipoCambio(TipoCambio.DCT)
            .potenzaKw(110)
            .potenzaCv(150)
            .consumoMedioLitri100km(new BigDecimal("5.8"))
            .prezzoListinoEur(new BigDecimal("35000"))
            .costoTagliandoBaseEur(new BigDecimal("250"))
            .intervalloTagliandoKm(15000)
            .gruppoAssicurativo(8)
            .build());

        motorizzazioneId = m.getId();
    }

    @Test
    @DisplayName("POST /calcolo — risposta 200 con giudizio e percentuale")
    void calcolo_successo() throws Exception {
        CalcoloRequestDTO req = CalcoloRequestDTO.builder()
            .motorizzazioneId(motorizzazioneId)
            .redditoNettoMensile(new BigDecimal("2500"))
            .acconto(new BigDecimal("5000"))
            .durataFinanziamentoMesi(60)
            .tanPercentuale(new BigDecimal("7.0"))
            .kmMensiliStimati(1500)
            .prezzoCombustibileLitro(new BigDecimal("1.85"))
            .build();

        mockMvc.perform(post("/api/v1/calcolo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.giudizio").isString())
            .andExpect(jsonPath("$.percentualeRedditoImpegnata").isNumber())
            .andExpect(jsonPath("$.rataFiananziamentoMensile").isNumber())
            .andExpect(jsonPath("$.totaleMensileAutoCompleto").isNumber())
            .andExpect(jsonPath("$.costoCarburanteMensile").value(closeTo(161.10, 0.10)))
            .andExpect(jsonPath("$.marcaModelloMotore").value(containsString("Volkswagen")));
    }

    @Test
    @DisplayName("POST /calcolo — 404 se motorizzazione inesistente")
    void calcolo_404_motorizzazione_assente() throws Exception {
        CalcoloRequestDTO req = CalcoloRequestDTO.builder()
            .motorizzazioneId(9999L)
            .redditoNettoMensile(new BigDecimal("2500"))
            .acconto(BigDecimal.ZERO)
            .durataFinanziamentoMesi(60)
            .tanPercentuale(new BigDecimal("7.0"))
            .kmMensiliStimati(1500)
            .prezzoCombustibileLitro(new BigDecimal("1.85"))
            .build();

        mockMvc.perform(post("/api/v1/calcolo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Risorsa non trovata"));
    }

    @Test
    @DisplayName("POST /calcolo — 400 se reddito mancante")
    void calcolo_400_reddito_assente() throws Exception {
        CalcoloRequestDTO req = CalcoloRequestDTO.builder()
            .motorizzazioneId(motorizzazioneId)
            // reddito mancante intenzionalmente
            .acconto(BigDecimal.ZERO)
            .durataFinanziamentoMesi(60)
            .tanPercentuale(new BigDecimal("7.0"))
            .kmMensiliStimati(1500)
            .prezzoCombustibileLitro(new BigDecimal("1.85"))
            .build();

        mockMvc.perform(post("/api/v1/calcolo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errori.redditoNettoMensile").exists());
    }

    @Test
    @DisplayName("POST /calcolo — 422 se acconto supera prezzo listino")
    void calcolo_422_acconto_troppo_alto() throws Exception {
        CalcoloRequestDTO req = CalcoloRequestDTO.builder()
            .motorizzazioneId(motorizzazioneId)
            .redditoNettoMensile(new BigDecimal("2500"))
            .acconto(new BigDecimal("50000")) // > 35000 prezzo listino
            .durataFinanziamentoMesi(60)
            .tanPercentuale(new BigDecimal("7.0"))
            .kmMensiliStimati(1500)
            .prezzoCombustibileLitro(new BigDecimal("1.85"))
            .build();

        mockMvc.perform(post("/api/v1/calcolo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.title").value("Errore nella richiesta"));
    }
}

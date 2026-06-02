package com.santofem.redditoauto.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.santofem.redditoauto.controller.dto.EstraiAutoRequestDTO;
import com.santofem.redditoauto.controller.dto.EstraiDaParametriRequestDTO;
import com.santofem.redditoauto.controller.dto.EstraiDaUrlRequestDTO;
import com.santofem.redditoauto.controller.dto.MotorizzazioneResponseDTO;
import com.santofem.redditoauto.service.AutoExtractionOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AutoController.class)
class AutoControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  AutoExtractionOrchestrator orchestrator;

    private MotorizzazioneResponseDTO sampleResponse() {
        return MotorizzazioneResponseDTO.builder()
            .id(1L)
            .marca("Volkswagen")
            .modello("Golf")
            .nomeMotore("2.0 TDI 150CV")
            .annoProduzione(2022)
            .build();
    }

    // ─── POST /estrai ─────────────────────────────────

    @Test
    @DisplayName("POST /estrai → 201 con body valido")
    void estraiDaTesto_ok() throws Exception {
        when(orchestrator.estraiDaTesto(anyString(), anyString()))
            .thenReturn(sampleResponse());

        String body = objectMapper.writeValueAsString(
            EstraiAutoRequestDTO.builder()
                .testoGrezzo("Volkswagen Golf 2.0 TDI 150 CV potenza 110 kW consumo 5.5 l/100km pneumatici 205/55 R16")
                .fonteDati("https://example.com")
                .build()
        );

        mvc.perform(post("/api/v1/auto/estrai")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.marca").value("Volkswagen"))
            .andExpect(jsonPath("$.modello").value("Golf"));
    }

    @Test
    @DisplayName("POST /estrai → 400 con testo troppo corto")
    void estraiDaTesto_testoTroppoCorto() throws Exception {
        String body = objectMapper.writeValueAsString(
            EstraiAutoRequestDTO.builder()
                .testoGrezzo("Golf")
                .build()
        );

        mvc.perform(post("/api/v1/auto/estrai")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /estrai → 400 con testo assente")
    void estraiDaTesto_testoMancante() throws Exception {
        mvc.perform(post("/api/v1/auto/estrai")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    // ─── POST /estrai-url ─────────────────────────────

    @Test
    @DisplayName("POST /estrai-url → 201 con URL valido")
    void estraiDaUrl_ok() throws Exception {
        when(orchestrator.estraiDaUrl(anyString(), anyString()))
            .thenReturn(sampleResponse());

        String body = objectMapper.writeValueAsString(
            EstraiDaUrlRequestDTO.builder()
                .url("https://www.autoscout24.it/auto/volkswagen/golf")
                .build()
        );

        mvc.perform(post("/api/v1/auto/estrai-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /estrai-url → 400 con URL non valido (no http)")
    void estraiDaUrl_urlNonValido() throws Exception {
        String body = objectMapper.writeValueAsString(
            EstraiDaUrlRequestDTO.builder()
                .url("ftp://non-valido.com")
                .build()
        );

        mvc.perform(post("/api/v1/auto/estrai-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /estrai-url → 400 con URL mancante")
    void estraiDaUrl_urlMancante() throws Exception {
        mvc.perform(post("/api/v1/auto/estrai-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    // ─── POST /estrai-parametri ───────────────────────

    @Test
    @DisplayName("POST /estrai-parametri → 201 con parametri validi")
    void estraiDaParametri_ok() throws Exception {
        when(orchestrator.estraiDaParametri(anyString(), anyString(), anyString(), anyInt()))
            .thenReturn(sampleResponse());

        String body = objectMapper.writeValueAsString(
            EstraiDaParametriRequestDTO.builder()
                .marca("Volkswagen")
                .modello("Golf")
                .motore("2.0 TDI 150CV")
                .anno(2022)
                .build()
        );

        mvc.perform(post("/api/v1/auto/estrai-parametri")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("POST /estrai-parametri → 400 con anno nel futuro lontano")
    void estraiDaParametri_annoFuturo() throws Exception {
        String body = objectMapper.writeValueAsString(
            EstraiDaParametriRequestDTO.builder()
                .marca("BMW")
                .modello("Serie 3")
                .motore("320d")
                .anno(2099)
                .build()
        );

        mvc.perform(post("/api/v1/auto/estrai-parametri")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /estrai-parametri → 400 con marca mancante")
    void estraiDaParametri_marcaMancante() throws Exception {
        String body = objectMapper.writeValueAsString(
            EstraiDaParametriRequestDTO.builder()
                .modello("Golf")
                .motore("2.0 TDI")
                .anno(2022)
                .build()
        );

        mvc.perform(post("/api/v1/auto/estrai-parametri")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }
}

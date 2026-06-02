package com.santofem.redditoauto.controller;

import com.santofem.redditoauto.controller.dto.MotorizzazioneResponseDTO;
import com.santofem.redditoauto.service.MotorizzazioneService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MotorizzazioneController.class)
class MotorizzazioneControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean MotorizzazioneService motorizzazioneService;

    private MotorizzazioneResponseDTO sampleDTO() {
        return MotorizzazioneResponseDTO.builder()
            .id(1L)
            .marca("Volkswagen")
            .modello("Golf")
            .nomeMotore("2.0 TDI 150CV")
            .annoProduzione(2022)
            .build();
    }

    @Test
    @DisplayName("GET /motorizzazioni/{id} \u2192 200 se esiste")
    void getById_ok() throws Exception {
        when(motorizzazioneService.findById(1L)).thenReturn(Optional.of(sampleDTO()));

        mvc.perform(get("/api/v1/motorizzazioni/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.marca").value("Volkswagen"))
            .andExpect(jsonPath("$.nomeMotore").value("2.0 TDI 150CV"));
    }

    @Test
    @DisplayName("GET /motorizzazioni/{id} \u2192 404 se non esiste")
    void getById_notFound() throws Exception {
        when(motorizzazioneService.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/motorizzazioni/99"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /motorizzazioni/cerca \u2192 200 con lista risultati")
    void cerca_ok() throws Exception {
        when(motorizzazioneService.cerca(anyString(), anyString(), anyInt()))
            .thenReturn(List.of(sampleDTO()));

        mvc.perform(get("/api/v1/motorizzazioni/cerca")
                .param("marca", "Volkswagen")
                .param("modello", "Golf")
                .param("anno", "2022"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    @DisplayName("GET /motorizzazioni/cerca \u2192 200 con lista vuota")
    void cerca_listaVuota() throws Exception {
        when(motorizzazioneService.cerca(anyString(), anyString(), anyInt()))
            .thenReturn(List.of());

        mvc.perform(get("/api/v1/motorizzazioni/cerca")
                .param("marca", "Ferrari")
                .param("modello", "Testarossa")
                .param("anno", "1985"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }
}

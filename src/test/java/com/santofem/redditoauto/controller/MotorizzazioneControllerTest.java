package com.santofem.redditoauto.controller;

import com.santofem.redditoauto.controller.dto.MotorizzazioneResponseDTO;
import com.santofem.redditoauto.service.MotorizzazioneService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MotorizzazioneController.class)
class MotorizzazioneControllerTest {

    @Autowired MockMvc mvc;
    @MockBean  MotorizzazioneService service;

    private MotorizzazioneResponseDTO sampleDto(Long id) {
        return MotorizzazioneResponseDTO.builder()
            .id(id)
            .marca("Toyota")
            .modello("Yaris")
            .nomeMotore("1.5 Hybrid")
            .annoProduzione(2023)
            .build();
    }

    // ─── GET /{id} ────────────────────────────────────

    @Test
    @DisplayName("GET /{id} → 200 con id esistente")
    void getById_found() throws Exception {
        when(service.findById(5L)).thenReturn(sampleDto(5L));

        mvc.perform(get("/api/v1/motorizzazioni/5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(5))
            .andExpect(jsonPath("$.marca").value("Toyota"));
    }

    @Test
    @DisplayName("GET /{id} → 404 con id inesistente")
    void getById_notFound() throws Exception {
        when(service.findById(999L)).thenThrow(new EntityNotFoundException("Non trovata"));

        mvc.perform(get("/api/v1/motorizzazioni/999"))
            .andExpect(status().isNotFound());
    }

    // ─── GET / (search) ───────────────────────────────

    @Test
    @DisplayName("GET / con parametri → 200 con lista risultati")
    void search_conParametri() throws Exception {
        when(service.search("Toyota", "Yaris", 2023))
            .thenReturn(List.of(sampleDto(1L), sampleDto(2L)));

        mvc.perform(get("/api/v1/motorizzazioni")
                .param("marca", "Toyota")
                .param("modello", "Yaris")
                .param("anno", "2023"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET / senza parametri → 200 con lista completa")
    void search_senzaParametri() throws Exception {
        when(service.search(null, null, null))
            .thenReturn(List.of(sampleDto(1L)));

        mvc.perform(get("/api/v1/motorizzazioni"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET / → 200 con lista vuota se nessun risultato")
    void search_listaVuota() throws Exception {
        when(service.search(anyString(), any(), any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/motorizzazioni")
                .param("marca", "Lamborghini"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    // ─── GET /autocomplete ────────────────────────────

    @Test
    @DisplayName("GET /autocomplete?q=golf → 200 con suggerimenti")
    void autocomplete_conRisultati() throws Exception {
        when(service.autocomplete("golf"))
            .thenReturn(List.of(
                "Volkswagen Golf 2.0 TDI 150CV (2022)",
                "Volkswagen Golf 1.5 TSI (2021)"
            ));

        mvc.perform(get("/api/v1/motorizzazioni/autocomplete")
                .param("q", "golf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0]").value("Volkswagen Golf 2.0 TDI 150CV (2022)"));
    }

    @Test
    @DisplayName("GET /autocomplete → 200 con lista vuota se nessun match")
    void autocomplete_nessunaCorrispondenza() throws Exception {
        when(service.autocomplete("xyz")).thenReturn(List.of());

        mvc.perform(get("/api/v1/motorizzazioni/autocomplete")
                .param("q", "xyz"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    // ─── GET /modello/{modelloId} ─────────────────────

    @Test
    @DisplayName("GET /modello/{id} → 200 con lista motorizzazioni")
    void getByModello_found() throws Exception {
        when(service.findByModello(3L))
            .thenReturn(List.of(sampleDto(1L), sampleDto(2L)));

        mvc.perform(get("/api/v1/motorizzazioni/modello/3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    // ─── PATCH /{id}/conferma ─────────────────────────

    @Test
    @DisplayName("PATCH /{id}/conferma → 200 con dati aggiornati")
    void conferma_ok() throws Exception {
        MotorizzazioneResponseDTO confirmed = sampleDto(7L);
        when(service.confermaManualmente(7L)).thenReturn(confirmed);

        mvc.perform(patch("/api/v1/motorizzazioni/7/conferma"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    @DisplayName("PATCH /{id}/conferma → 404 se non trovata")
    void conferma_notFound() throws Exception {
        when(service.confermaManualmente(99L))
            .thenThrow(new EntityNotFoundException("Non trovata"));

        mvc.perform(patch("/api/v1/motorizzazioni/99/conferma"))
            .andExpect(status().isNotFound());
    }

    // ─── DELETE /{id} ─────────────────────────────────

    @Test
    @DisplayName("DELETE /{id} → 204 con id esistente")
    void delete_ok() throws Exception {
        doNothing().when(service).delete(10L);

        mvc.perform(delete("/api/v1/motorizzazioni/10"))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /{id} → 404 se non trovata")
    void delete_notFound() throws Exception {
        doThrow(new EntityNotFoundException("Non trovata")).when(service).delete(99L);

        mvc.perform(delete("/api/v1/motorizzazioni/99"))
            .andExpect(status().isNotFound());
    }
}

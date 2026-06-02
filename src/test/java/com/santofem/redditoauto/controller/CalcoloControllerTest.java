package com.santofem.redditoauto.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.santofem.redditoauto.service.CalcoloSostenibilitaService;
import com.santofem.redditoauto.service.dto.CalcoloRequestDTO;
import com.santofem.redditoauto.service.dto.CalcoloRispostaDTO;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CalcoloController.class)
class CalcoloControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean CalcoloSostenibilitaService calcoloService;

    private CalcoloRequestDTO validRequest() {
        return CalcoloRequestDTO.builder()
            .motorizzazioneId(1L)
            .redditoNettoMensile(new BigDecimal("2000.00"))
            .acconto(new BigDecimal("2000.00"))
            .durataFinanziamentoMesi(60)
            .tanPercentuale(new BigDecimal("7.0"))
            .kmMensiliStimati(1500)
            .prezzoCombustibileLitro(new BigDecimal("1.85"))
            .build();
    }

    private CalcoloRispostaDTO sampleRisposta(String giudizio, boolean sostenibile) {
        return CalcoloRispostaDTO.builder()
            .marcaModelloMotore("Volkswagen Golf 2.0 TDI (2022)")
            .rataFiananziamentoMensile(new BigDecimal("350.00"))
            .totaleCostiViviMensili(new BigDecimal("280.00"))
            .totaleMensileAutoCompleto(new BigDecimal("630.00"))
            .redditoNettoMensile(new BigDecimal("2000.00"))
            .percentualeRedditoImpegnata(new BigDecimal("31.50"))
            .sostenibile(sostenibile)
            .giudizio(giudizio)
            .messaggioConsigli("Consiglio di test")
            .build();
    }

    @Test
    @DisplayName("POST /calcolo → 200 con body valido e giudizio ACCETTABILE")
    void calcola_ok_accettabile() throws Exception {
        when(calcoloService.calcola(any())).thenReturn(sampleRisposta("ACCETTABILE", true));

        mvc.perform(post("/api/v1/calcolo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.giudizio").value("ACCETTABILE"))
            .andExpect(jsonPath("$.sostenibile").value(true))
            .andExpect(jsonPath("$.totaleMensileAutoCompleto").value(630.00));
    }

    @Test
    @DisplayName("POST /calcolo → 200 con giudizio CRITICO")
    void calcola_ok_critico() throws Exception {
        when(calcoloService.calcola(any())).thenReturn(sampleRisposta("CRITICO", false));

        mvc.perform(post("/api/v1/calcolo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.giudizio").value("CRITICO"))
            .andExpect(jsonPath("$.sostenibile").value(false));
    }

    @Test
    @DisplayName("POST /calcolo → 404 se motorizzazione non esiste")
    void calcola_motorizzazioneNotFound() throws Exception {
        when(calcoloService.calcola(any()))
            .thenThrow(new EntityNotFoundException("Motorizzazione non trovata con id: 99"));

        mvc.perform(post("/api/v1/calcolo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /calcolo → 400 con reddito mancante")
    void calcola_redditoMancante() throws Exception {
        CalcoloRequestDTO req = validRequest().toBuilder()
            .redditoNettoMensile(null)
            .build();

        mvc.perform(post("/api/v1/calcolo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /calcolo → 400 con durata < 12 mesi")
    void calcola_durataTroppoCorta() throws Exception {
        CalcoloRequestDTO req = validRequest().toBuilder()
            .durataFinanziamentoMesi(6)
            .build();

        mvc.perform(post("/api/v1/calcolo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /calcolo → 400 con TAN > 30%")
    void calcola_tanEccessivo() throws Exception {
        CalcoloRequestDTO req = validRequest().toBuilder()
            .tanPercentuale(new BigDecimal("35.0"))
            .build();

        mvc.perform(post("/api/v1/calcolo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }
}

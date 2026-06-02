package com.santofem.redditoauto.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.santofem.redditoauto.service.CalcoloSostenibilitaService;
import com.santofem.redditoauto.service.dto.CalcoloRequestDTO;
import com.santofem.redditoauto.service.dto.CalcoloRispostaDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CalcoloControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean CalcoloSostenibilitaService calcoloService;

    @Test
    @DisplayName("[IT] POST /calcolo → 200 con dati validi")
    void calcola_integrazione_ok() throws Exception {
        CalcoloRispostaDTO risposta = CalcoloRispostaDTO.builder()
            .marcaModelloMotore("Volkswagen Golf 2.0 TDI (2022)")
            .totaleMensileAutoCompleto(new BigDecimal("600.00"))
            .redditoNettoMensile(new BigDecimal("2000.00"))
            .percentualeRedditoImpegnata(new BigDecimal("30.00"))
            .sostenibile(true)
            .giudizio("ACCETTABILE")
            .messaggioConsigli("Test OK")
            .build();

        when(calcoloService.calcola(any())).thenReturn(risposta);

        CalcoloRequestDTO request = CalcoloRequestDTO.builder()
            .motorizzazioneId(1L)
            .redditoNettoMensile(new BigDecimal("2000.00"))
            .build();

        mvc.perform(post("/api/v1/calcolo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.giudizio").value("ACCETTABILE"));
    }
}

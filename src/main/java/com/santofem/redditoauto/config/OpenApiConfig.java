package com.santofem.redditoauto.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configurazione OpenAPI 3 / Swagger UI.
 *
 * UI disponibile su: http://localhost:8080/swagger-ui.html
 * Spec JSON:         http://localhost:8080/v3/api-docs
 * Spec YAML:         http://localhost:8080/v3/api-docs.yaml
 *
 * In produzione Swagger UI viene disabilitato tramite
 * springdoc.swagger-ui.enabled=false in application-prod.properties.
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:RedditoAuto}")
    private String appName;

    @Bean
    public OpenAPI redditoAutoOpenAPI() {
        return new OpenAPI()
            .info(buildInfo())
            .servers(buildServers())
            .components(new Components()); // estendibile con SecuritySchemes in futuro
    }

    private Info buildInfo() {
        return new Info()
            .title(appName + " API")
            .version("1.0.0")
            .description("""
                ## RedditoAuto — Calcolatore di Sostenibilità Economica

                Questa API permette di:
                - **Acquisire dati tecnici** di un'auto tramite AI (Google Gemini) + Web Scraping
                - **Consultare il catalogo** delle motorizzazioni salvate nel DB
                - **Calcolare la sostenibilità** economica mensile (rata + costi vivi + giudizio)

                ### Flusso tipico
                1. `POST /api/v1/auto/estrai-parametri` — cerca e salva i dati dell'auto
                2. `GET  /api/v1/motorizzazioni/{id}` — verifica i dati estratti
                3. `POST /api/v1/calcolo` — calcola rata, costi vivi e giudizio

                ### Giudizi di sostenibilità
                | Giudizio | % Reddito impegnata |
                |---|---|
                | OTTIMO | < 20% |
                | ACCETTABILE | 20% – 30% |
                | ATTENZIONE | 30% – 40% |
                | CRITICO | > 40% |
                """)
            .contact(new Contact()
                .name("Santo Femiano")
                .url("https://github.com/SantoFemiano/redditoAuto"))
            .license(new License()
                .name("MIT License")
                .url("https://opensource.org/licenses/MIT"));
    }

    private List<Server> buildServers() {
        Server local = new Server()
            .url("http://localhost:8081")
            .description("Sviluppo locale");

        Server prod = new Server()
            .url("https://api.redditoauto.it")
            .description("Produzione (Oracle Cloud)");

        return List.of(local, prod);
    }



}

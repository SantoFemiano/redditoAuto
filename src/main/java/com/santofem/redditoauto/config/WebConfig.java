package com.santofem.redditoauto.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configurazione CORS per l'integrazione con il frontend Angular.
 * <p>
 * Le origini permesse sono configurabili tramite properties:
 *   cors.allowed-origins=<a href="http://localhost:4200,https://redditoauto.vercel.app">...</a>
 * <p>
 * In dev: permette localhost:4200 (Angular dev server).
 * In prod: permette solo il dominio Vercel ufficiale.
 * <p>
 * Nota: Spring Boot 4.x richiede che le origini siano esplicite
 * quando allowCredentials=true (no wildcard "*").
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins:http://localhost:4200}")
    private String[] allowedOrigins;

    @Value("${cors.max-age:3600}")
    private long maxAge;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**") // Applica CORS a tutti gli endpoint che iniziano con /api
                .allowedOrigins("http://localhost:3000", "http://localhost:5173", "https://v0.dev", "https://bolt.new") // I domini del frontend consentiti
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Metodi HTTP consentiti
                .allowedHeaders("*") // Consente tutti gli header (Content-Type, Authorization, ecc.)
                .allowCredentials(true); // Consente l'invio di cookie o token di sessione se necessario
    }
}

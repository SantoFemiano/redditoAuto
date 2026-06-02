package com.santofem.redditoauto.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configurazione CORS per l'integrazione con il frontend Angular.
 *
 * Le origini permesse sono configurabili tramite properties:
 *   cors.allowed-origins=http://localhost:4200,https://redditoauto.vercel.app
 *
 * In dev: permette localhost:4200 (Angular dev server).
 * In prod: permette solo il dominio Vercel ufficiale.
 *
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
        registry.addMapping("/api/**")
            .allowedOrigins(allowedOrigins)
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders("Location", "X-Request-Id")
            .allowCredentials(true)
            .maxAge(maxAge); // Pre-flight cache in secondi
    }
}

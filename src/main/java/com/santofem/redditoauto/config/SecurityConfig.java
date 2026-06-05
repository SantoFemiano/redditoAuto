package com.santofem.redditoauto.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configurazione Spring Security.
 *
 * Profilo attuale: DEVELOPMENT — tutte le API pubbliche.
 * Per produzione:
 *  1. Rimuovere .permitAll() sulle API
 *  2. Aggiungere filtro JWT (JwtAuthenticationFilter)
 *  3. Restringere CORS a origini specifiche (es. frontend Vercel)
 *  4. Aggiungere rate limiting con Bucket4j
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF disabilitato: API REST stateless (nessuna sessione browser)
                .csrf(AbstractHttpConfigurer::disable)
                // CORS configurato dal bean corsConfigurationSource()
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Stateless: nessuna sessione HTTP
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Endpoint pubblici sempre accessibili
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()
                        // TODO [PROD]: sostituire con .authenticated() e aggiungere JWT filter
                        .anyRequest().permitAll()
                )
                .build();
    }

    /**
     * CORS permissivo per sviluppo.
     * In produzione sostituire "*" con le origini reali:
     *   List.of("https://reddito-auto.vercel.app", "https://tuodominio.com")
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

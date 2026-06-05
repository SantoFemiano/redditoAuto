package com.santofem.redditoauto.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Attiva il meccanismo di JPA Auditing di Spring Data.
 * Necessario affinché @CreatedDate e @LastModifiedDate
 * in BaseAuditEntity vengano popolati automaticamente.
 *
 * Separato in una classe dedicata per evitare conflitti
 * con @SpringBootApplication durante i test slice (@DataJpaTest).
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
    // Nessun bean aggiuntivo necessario.
    // Se in futuro si aggiunge @CreatedBy/@LastModifiedBy,
    // aggiungere qui un bean AuditorAware<String>.
}

package com.santofem.redditoauto.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Configurazione cache Caffeine in-memory.
 *
 * Cache definite:
 *  - calcoli      : risultati CalcoloSostenibilitaService  → TTL 10 min, max 500 entry
 *  - motorizzazioni: lista motorizzazioni per modello       → TTL 30 min, max 1000 entry
 *  - marche       : lista marche                           → TTL 60 min, max 200 entry
 *  - modelli      : lista modelli per marca                → TTL 60 min, max 500 entry
 *
 * In produzione, valuta di sostituire con Redis (spring.cache.type=redis)
 * per cache distribuita multi-istanza senza alcuna modifica al codice applicativo.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheNames(List.of("calcoli", "motorizzazioni", "marche", "modelli"));
        manager.setCaffeine(defaultSpec());
        return manager;
    }

    /**
     * Spec Caffeine di default.
     * - expireAfterWrite: TTL dalla scrittura (stale-after-write)
     * - maximumSize: eviction LRU quando si supera il limite
     * - recordStats(): espone metriche via Actuator /actuator/caches
     */
    private Caffeine<Object, Object> defaultSpec() {
        return Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(1_000)
                .recordStats();
    }
}

package com.santofem.redditoauto.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configura Caffeine come cache manager in-memory (L1).
 * In produzione si può affiancare Redis come L2 distribuita.
 *
 * Cache registrate:
 *  - calcoli          → risultati CalcoloSostenibilitaService, TTL 10 min, max 1000 entries
 *  - motorizzazioni   → lookup entity, TTL 10 min, max 1000 entries
 *  - lookup           → brand/model lookup, TTL 10 min, max 1000 entries
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("calcoli", "motorizzazioni", "lookup");
        manager.setCaffeine(
                Caffeine.newBuilder()
                        .maximumSize(1_000)
                        .expireAfterWrite(Duration.ofMinutes(10))
                        .recordStats()
        );
        return manager;
    }
}

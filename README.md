# RedditoAuto

Calcolatore di sostenibilità economica per auto — backend enterprise Spring Boot 4.

## Stack

- **Spring Boot 4.0.6** + **Java 25**
- **PostgreSQL** + **Flyway** per le migrazioni
- **LangChain4j + Gemini** per l'estrazione AI dei dati auto
- **Caffeine** (cache L1 in-memory) + **Redis** (cache L2 distribuita in prod)
- **Spring Security** con Basic Auth (estendibile a JWT/OAuth2)
- **MapStruct** per mapping entity↔DTO type-safe
- **SpringDoc OpenAPI 2.8.8** per la documentazione Swagger
- **Spring Data JPA Auditing** per audit trail automatico
- **Bucket4j** per rate limiting per IP
- **Micrometer Tracing** per distributed tracing

## Architettura

```
redditoauto/
├── config/                   # CacheConfig, SecurityConfig, OpenApiConfig, BolloAciProperties
├── controller/               # REST controllers (AutoController, CalcoloController, MotorizzazioneController)
├── entity/                   # JPA entities + BaseAuditEntity + enums
├── exception/                # ApiExceptionHandler (RFC 7807 ProblemDetail)
├── mapper/                   # MapStruct mappers
├── repository/               # Spring Data JPA repositories
├── service/                  # Business logic + CalcoloSostenibilitaService (@Cacheable)
├── ai/                       # LangChain4j AI extractors
├── acquisition/              # Data acquisition facade
└── scraper/                  # Web scraping
```

## Enterprise upgrades (v2)

- ✅ `CacheConfig` — Caffeine cache manager (`calcoli`, `motorizzazioni`, `lookup`)
- ✅ `SecurityConfig` — Spring Security, permit Swagger/Actuator, Basic Auth
- ✅ `BolloAciProperties` — tariffe bollo ACI esternalizzate in `application.properties`
- ✅ `BaseAuditEntity` — audit trail automatico `createdAt`/`updatedAt`
- ✅ `ApiExceptionHandler` — `@RestControllerAdvice` con RFC 7807 `ProblemDetail`
- ✅ `MotorizzazioneMapper` — MapStruct mapper type-safe
- ✅ `@Cacheable` su `CalcoloSostenibilitaService.calcola()`
- ✅ `@EnableJpaAuditing` + `@EnableConfigurationProperties`
- ✅ Micrometer Tracing, AOP, Redis starter aggiunti al pom
- ✅ jsoup aggiornato a 1.18.3

# 🚗 RedditoAuto — Car Affordability Calculator

<div align="center">

[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-latest-blue?logo=postgresql)](https://www.postgresql.org/)
[![Flyway](https://img.shields.io/badge/Flyway-migrations-red)](https://flywaydb.org/)
[![LangChain4j](https://img.shields.io/badge/AI-LangChain4j%20%2B%20Gemini-purple?logo=google)](https://docs.langchain4j.dev/)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](LICENSE)

**REST API that calculates the monthly economic sustainability of buying a car.**
Built with Spring Boot 4 · Java 25 · PostgreSQL · Flyway · LangChain4j · Google Gemini AI · Jsoup Web Scraping

[📖 Swagger UI](#-api-documentation-swagger)

</div>

---

## 📌 Project Overview

**RedditoAuto** is a backend REST API that helps users evaluate whether they can **afford a specific car** based on their monthly net income. The system combines **AI-powered car data extraction** (via Google Gemini + LangChain4j) with a detailed **financial sustainability calculator** that models monthly installment payments, running costs (fuel, insurance, maintenance), and produces a final verdict: `OTTIMO` / `ACCETTABILE` / `ATTENZIONE` / `CRITICO`.

The project is a showcase of **AI integration in a real-world Spring Boot application**, demonstrating how to combine LLM-based data extraction, web scraping, and financial domain logic in a clean, layered architecture.

### Key Highlights for Recruiters

- ✅ **AI-powered data extraction** using Google Gemini via LangChain4j — 3 distinct extraction strategies
- ✅ **Web scraping module** (Jsoup) to automatically retrieve car spec sheets from any URL
- ✅ **Automated car search**: given make/model/engine/year, the system finds and extracts specs autonomously
- ✅ **Financial sustainability engine** with installment calculation (TAN), running costs, and verdict scoring
- ✅ **Flyway database migrations** — schema fully versioned and reproducible
- ✅ **Multi-profile configuration** (`dev` / `prod`) with HikariCP connection pooling
- ✅ **Java 25 + Spring Boot 4** — bleeding-edge stack
- ✅ **H2 in-memory database** for test isolation
- ✅ **Spring Actuator** for health and metrics endpoints
- ✅ **Full OpenAPI/Swagger** documentation with operation descriptions

---

## 🛠️ Tech Stack

| Layer | Technology | Version | Purpose |
|---|---|---|---|
| **Language** | Java | 25 | Core language (latest) |
| **Framework** | Spring Boot | 4.0.6 | Application framework |
| **Web** | Spring Web MVC | — | REST layer |
| **Persistence** | Spring Data JPA + Hibernate | — | ORM & database access |
| **Database** | PostgreSQL | latest | Production relational store |
| **Migrations** | Flyway | — | Versioned schema migrations |
| **Connection Pool** | HikariCP | — | DB connection pooling |
| **AI** | LangChain4j + Google Gemini | 0.36.0 | Car data extraction via LLM |
| **Web Scraping** | Jsoup | 1.17.2 | HTML scraping of car spec sheets |
| **Validation** | Spring Validation | — | Request input validation |
| **Documentation** | SpringDoc OpenAPI | 2.8.8 | Swagger UI |
| **Observability** | Spring Actuator | — | Health & metrics endpoints |
| **Test DB** | H2 | — | In-memory DB for test slices |
| **Build** | Maven Wrapper | — | Build & dependency management |

---

## 🏗️ Architecture

The API follows a **Layered Architecture** with a dedicated AI module and scraper module, keeping extraction logic cleanly separated from the financial domain:

```
src/main/java/com/santofem/redditoauto/
│
├── RedditoAutoApplication.java         # Entry point
│
├── controller/                         # REST layer
│   ├── AutoController.java             # /api/v1/auto — AI-powered car data extraction
│   ├── CalcoloController.java          # /api/v1/calcolo — sustainability calculation
│   ├── MotorizzazioneController.java   # /api/v1/motorizzazione — car specs CRUD
│   └── dto/                            # Request/Response DTOs
│
├── service/                            # Business logic
│   ├── CalcoloSostenibilitaService     # Financial sustainability engine
│   ├── AutoExtractionOrchestrator      # Orchestrates 3 extraction strategies
│   └── dto/                            # Service-level DTOs
│
├── ai/                                 # AI integration module
│   ├── AiCarDataExtractor.java         # LangChain4j + Gemini: text → structured data
│   ├── AiDirectDataProvider.java       # Gemini direct data provider for parametric search
│   └── dto/                            # AI response DTOs
│
├── scraper/                            # Web scraping module
│   └── (Jsoup-based HTML scrapers)     # Retrieves raw text from car spec URLs
│
├── entity/                             # JPA entities
│   ├── Marca.java                      # Car brand
│   ├── Modello.java                    # Car model
│   ├── Motorizzazione.java             # Engine/trim with full technical specs
│   └── enums/                          # TipoCarburante, TipoCambio, ClasseEuro, etc.
│
├── repository/                         # Spring Data JPA repositories
├── mapper/                             # Entity ↔ DTO mappers
├── exception/                          # Custom exception handling
└── config/                             # CORS, Swagger, app configuration
```

### AI Extraction Flow

The system supports **3 extraction strategies** for car technical data:

```
Strategy 1 — Raw Text
  User pastes spec sheet text
        │
        ▼
  Gemini AI parses → structured MotorizzazioneDTO

Strategy 2 — URL Scraping
  User provides car URL
        │
        ▼
  Jsoup scrapes HTML → extracts text
        │
        ▼
  Gemini AI parses → structured MotorizzazioneDTO

Strategy 3 — Parametric (brand + model + engine + year)
  User sends make/model/engine/year
        │
        ▼
  AiDirectDataProvider searches & retrieves specs
        │
        ▼
  Gemini AI parses → structured MotorizzazioneDTO
```

### Sustainability Calculation Flow

```
Input: motorizzazioneId + net income + financing params
        │
        ▼
  CalcoloSostenibilitaService
  ├── Monthly installment (TAN + duration + down payment)
  ├── Monthly fuel cost (km/month × consumption × price/L)
  ├── Monthly insurance (annual ÷ 12, or estimated if missing)
  ├── Monthly maintenance (fixed estimate by car category)
  └── Total monthly car cost
        │
        ▼
  % of income committed → Verdict
  ├── ≤ 15%  → OTTIMO
  ├── ≤ 20%  → ACCETTABILE
  ├── ≤ 25%  → ATTENZIONE
  └── > 25%  → CRITICO
```

---

## 🔌 API Endpoints

### 🤖 Auto Extraction — `/api/v1/auto`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/auto/estrai` | Extract car specs from raw pasted text (Gemini AI) |
| `POST` | `/api/v1/auto/estrai-url` | Extract car specs from a URL (Jsoup scraping + Gemini AI) |
| `POST` | `/api/v1/auto/estrai-parametri` | Extract specs by make/model/engine/year (autonomous search + Gemini AI) |

#### Example — Parametric extraction request

```json
POST /api/v1/auto/estrai-parametri
{
  "marca": "Volkswagen",
  "modello": "Golf",
  "motore": "2.0 TDI",
  "anno": 2022,
  "potenzaCv": 150,
  "tipoCarburante": "DIESEL",
  "tipoCambio": "DSG"
}
```

### 💶 Sustainability Calculation — `/api/v1/calcolo`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/calcolo` | Calculate full monthly cost and sustainability verdict |

#### Example — Calculation request & response

```json
POST /api/v1/calcolo
{
  "motorizzazioneId": 42,
  "redditoNettoMensile": 2000.00,
  "acconto": 3000.00,
  "durataFinanziamentoMesi": 60,
  "tanPercentuale": 7.5,
  "kmMensiliStimati": 1500,
  "prezzoCombustibileLitro": 1.85,
  "assicurazioneAnnuaEur": 850.00
}
```

```json
// Response
{
  "rataMensile": 520.00,
  "costoCarburanteMensile": 95.00,
  "assicurazioneMensile": 70.83,
  "manutenzioneStimata": 40.00,
  "totaleMensileAutoCompleto": 725.83,
  "percentualeRedditoImpegnata": 36.3,
  "giudizio": "CRITICO",
  "sostenibile": false,
  "consigli": ["Considera un acconto maggiore...", "Valuta un'auto usata..."]
}
```

### 🚗 Motorizzazione — `/api/v1/motorizzazione`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/motorizzazione` | List all saved engine specs |
| `GET` | `/api/v1/motorizzazione/{id}` | Get a specific engine spec |
| `DELETE` | `/api/v1/motorizzazione/{id}` | Delete an engine spec |

> 📖 Full interactive documentation available at runtime via [Swagger UI](#-api-documentation-swagger).

---

## 🗃️ Domain Model

| Entity | Description |
|---|---|
| `Marca` | Car brand (e.g. Volkswagen, BMW) |
| `Modello` | Car model linked to a brand (e.g. Golf, Serie 3) |
| `Motorizzazione` | Full engine/trim spec: fuel type, displacement, power (CV), torque, consumption, CO2, gearbox, Euro standard |

Enums: `TipoCarburante` (BENZINA, DIESEL, IBRIDO, ELETTRICO, GPL, METANO), `TipoCambio` (MANUALE, AUTOMATICO, DSG, CVT), `ClasseEuro`.

Database schema is fully managed by **Flyway migrations** located in `src/main/resources/db/migration`.

---

## ⚙️ Configuration Profiles

| Profile | Port | Notes |
|---|---|---|
| `dev` | 8081 | Local PostgreSQL, verbose logging, H2 for tests |
| `prod` | 8080 | Remote PostgreSQL, behind reverse proxy, minimal logging |

Activate via environment variable: `SPRING_PROFILES_ACTIVE=dev` or `SPRING_PROFILES_ACTIVE=prod`

---

## 🚀 Local Setup

### Prerequisites

- Java 25+
- PostgreSQL (running locally or via Docker)
- Maven (or use `./mvnw`)

### 1. Clone the repository

```bash
git clone https://github.com/SantoFemiano/redditoAuto.git
cd redditoAuto
```

### 2. Create the database

```bash
psql -U postgres -c "CREATE DATABASE redditoauto;"
```

### 3. Configure environment variables

```bash
export DB_URL=jdbc:postgresql://localhost:5432/redditoauto
export DB_USERNAME=postgres
export DB_PASSWORD=yourpassword
export GEMINI_API_KEY=your-gemini-api-key
export SPRING_PROFILES_ACTIVE=dev
export CORS_ALLOWED_ORIGINS=http://localhost:4200
```

### 4. Run (Flyway will auto-apply migrations on startup)

```bash
./mvnw spring-boot:run
```

API available at: `http://localhost:8081`

---

## 📚 API Documentation (Swagger)

Once the app is running:

```
http://localhost:8081/swagger-ui/index.html
```

All endpoints are fully annotated with `@Operation` descriptions and request/response schema via SpringDoc OpenAPI.

---

## 🌍 Environment Variables

| Variable | Description | Example |
|---|---|---|
| `DB_URL` | JDBC PostgreSQL connection URL | `jdbc:postgresql://localhost:5432/redditoauto` |
| `DB_USERNAME` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | `secret` |
| `GEMINI_API_KEY` | Google Gemini API key for AI extraction | `AIza...` |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `dev` or `prod` |
| `CORS_ALLOWED_ORIGINS` | Allowed CORS origins | `http://localhost:4200` |

> ⚠️ Never commit real credentials. All sensitive values are injected via environment variables.

---

## 🧪 Testing

```bash
# Run all tests (uses H2 in-memory DB — no PostgreSQL required)
./mvnw test
```

Test slices (`@WebMvcTest`, `@AutoConfigureMockMvc`) use the dedicated `spring-boot-starter-webmvc-test` module introduced in Spring Boot 4, with H2 replacing PostgreSQL for complete test isolation.

---

## 👤 Author

**Santo Femiano**
- GitHub: [@SantoFemiano](https://github.com/SantoFemiano)

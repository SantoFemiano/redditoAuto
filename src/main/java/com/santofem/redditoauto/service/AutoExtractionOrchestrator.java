package com.santofem.redditoauto.service;

import com.santofem.redditoauto.ai.AiCarDataExtractor;
import com.santofem.redditoauto.ai.AiDirectDataProvider;
import com.santofem.redditoauto.ai.dto.CarDataDTO;
import com.santofem.redditoauto.controller.dto.MotorizzazioneResponseDTO;
import com.santofem.redditoauto.entity.Marca;
import com.santofem.redditoauto.entity.Modello;
import com.santofem.redditoauto.entity.Motorizzazione;
import com.santofem.redditoauto.exception.GeminiUnavailableException;
import com.santofem.redditoauto.mapper.CarDataMapper;
import com.santofem.redditoauto.repository.MarcaRepository;
import com.santofem.redditoauto.repository.ModelloRepository;
import com.santofem.redditoauto.repository.MotorizzazioneRepository;
import com.santofem.redditoauto.scraper.ScraperResult;
import com.santofem.redditoauto.scraper.WebScraper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutoExtractionOrchestrator {

    /** Numero massimo di caratteri del testo scraping inviato a Gemini.
     *  Valori più alti aumentano il rischio di output token troncati (MalformedJsonException). */
    private static final int MAX_SCRAPING_CHARS = 4500;

    private final WebScraper webScraper;
    private final AiCarDataExtractor aiExtractor;
    private final AiDirectDataProvider aiDirectProvider;
    private final CarDataMapper carDataMapper;
    private final MarcaRepository marcaRepository;
    private final ModelloRepository modelloRepository;
    private final MotorizzazioneRepository motorizzazioneRepository;

    // -----------------------------------------------
    // ENDPOINT: /estrai-parametri (principale)
    // -----------------------------------------------

    @Transactional
    public MotorizzazioneResponseDTO estraiDaParametri(
            String marca, String modello, String motore, int anno,
            int potenzaCv, String tipoCarburante, String tipoCambio) {

        log.info("[Orchestratore] Richiesta estrazione: {} {} {} {} (cv={} carb={} cambio={})",
            marca, modello, motore, anno, potenzaCv, tipoCarburante, tipoCambio);

        // 1. Cache DB
        List<Motorizzazione> esistenti = motorizzazioneRepository
            .findByMarcaModelloAnno(marca, modello, anno);

        if (!esistenti.isEmpty()) {
            Optional<Motorizzazione> exactMatch = esistenti.stream()
                .filter(m -> motore.equalsIgnoreCase(m.getNomeMotore()))
                .findFirst();
            if (exactMatch.isPresent()) {
                log.info("[Orchestratore] Cache hit esatto: motorizzazione id={}", exactMatch.get().getId());
                return carDataMapper.toResponseDTO(exactMatch.get());
            }
            Optional<Motorizzazione> partialMatch = esistenti.stream()
                .filter(m -> m.getNomeMotore() != null
                    && !isPlaceholder(m.getNomeMotore())
                    && (motore.toLowerCase().contains(m.getNomeMotore().toLowerCase())
                        || m.getNomeMotore().toLowerCase().contains(motore.toLowerCase())))
                .findFirst();
            if (partialMatch.isPresent()) {
                log.info("[Orchestratore] Cache hit parziale: motorizzazione id={} nome='{}'",
                    partialMatch.get().getId(), partialMatch.get().getNomeMotore());
                return carDataMapper.toResponseDTO(partialMatch.get());
            }
        }

        log.info("[Orchestratore] Cache miss: avvio scraping per {} {} {}", marca, modello, motore);

        // 2. Scraping web con parametri estesi per motor-scoring
        ScraperResult scraperResult = webScraper.scrapeConRisultato(
            marca, modello, motore, anno, potenzaCv, tipoCarburante, tipoCambio);

        CarDataDTO dto;
        String fonteDati;
        String warningAnno = null;

        if (scraperResult.hasText()) {
            // Usa l'anno EFFETTIVO trovato dallo scraper (non quello richiesto dall'utente)
            int annoEffettivo = scraperResult.annoEffettivo();
            warningAnno = scraperResult.buildWarningAnno();

            if (scraperResult.annoFallback()) {
                log.warn("[Orchestratore] Anno richiesto={} non disponibile: salvataggio con anno effettivo={}",
                    anno, annoEffettivo);
            }

            // Tronca il testo per evitare che Gemini superi il limite di output token
            // e produca JSON troncato (MalformedJsonException su $.nomeMotore e simili)
            String testoScraping = truncaTestoScraping(scraperResult.testo());

            log.info("[Orchestratore] Scraping riuscito ({} chars → {} chars dopo troncamento), invio all'AI extractor",
                scraperResult.testo().length(), testoScraping.length());
            CarDataDTO raw = callAiSafely(() -> aiExtractor.extractCarData(
                marca, modello, motore, String.valueOf(annoEffettivo), testoScraping));
            dto = overrideIdentita(raw, marca, modello, motore, annoEffettivo);
            fonteDati = "scraping:auto-data.net:" + marca + ":" + modello + ":" + annoEffettivo;
        } else {
            log.warn("[Orchestratore] Scraping fallito. Fallback AI-direct per {} {} {} {}",
                marca, modello, motore, anno);
            CarDataDTO raw = callAiSafely(() -> aiDirectProvider.getCarData(
                marca, modello, motore, String.valueOf(anno)));
            dto = overrideIdentita(raw, marca, modello, motore, anno);
            fonteDati = "ai-direct:" + marca + ":" + modello + ":" + anno;
        }

        MotorizzazioneResponseDTO response = persistiDto(dto, fonteDati);
        // Propaga il warning anno al frontend
        if (warningAnno != null) {
            response.setWarningAnno(warningAnno);
            log.info("[Orchestratore] Warning anno propagato: {}", warningAnno);
        }
        return response;
    }

    /**
     * Overload di compatibilità per chiamate senza i nuovi parametri opzionali.
     */
    @Transactional
    public MotorizzazioneResponseDTO estraiDaParametri(
            String marca, String modello, String motore, int anno) {
        return estraiDaParametri(marca, modello, motore, anno, 0, null, null);
    }

    // -----------------------------------------------
    // ENDPOINT: /estrai-url
    // -----------------------------------------------

    @Transactional
    public MotorizzazioneResponseDTO estraiDaUrl(String url, String fonteDati) {
        log.info("[Orchestratore] Estrazione da URL: {}", url);
        String testo = webScraper.scrapeUrl(url)
            .orElseThrow(() ->
                new IllegalStateException("Impossibile estrarre testo dall'URL: " + url));
        CarDataDTO dto = callAiSafely(() -> aiExtractor.extractCarData(
            "sconosciuta", "sconosciuto", "sconosciuto", "0", truncaTestoScraping(testo)));
        return persistiDto(dto, fonteDati);
    }

    // -----------------------------------------------
    // ENDPOINT: /estrai (testo grezzo)
    // -----------------------------------------------

    @Transactional
    public MotorizzazioneResponseDTO estraiDaTesto(String testoGrezzo, String fonteDati) {
        CarDataDTO dto = callAiSafely(() -> aiExtractor.extractCarData(
            "sconosciuta", "sconosciuto", "sconosciuto", "0", truncaTestoScraping(testoGrezzo)));
        return persistiDto(dto, fonteDati);
    }

    // -----------------------------------------------
    // OVERRIDE POST-AI
    // -----------------------------------------------

    private CarDataDTO overrideIdentita(
            CarDataDTO raw, String marca, String modello, String motore, int anno) {

        boolean placeholderRilevato =
            isPlaceholder(raw.marca()) ||
            isPlaceholder(raw.modello()) ||
            isPlaceholder(raw.nomeMotore());

        if (placeholderRilevato) {
            log.warn("[AI] Placeholder rilevato nel DTO raw: marca='{}' modello='{}' motore='{}'. "
                   + "Override applicato con i valori frontend.",
                raw.marca(), raw.modello(), raw.nomeMotore());
        }

        return new CarDataDTO(
            marca,
            modello,
            motore,
            anno,
            raw.tipoCarburante(),
            raw.tipoCambio(),
            raw.potenzaKw(),
            raw.potenzaCv(),
            raw.cilindrataCC(),
            raw.consumoMedioLitri100km(),
            raw.consumoUrbanoLitri100km(),
            raw.consumoExtraurbanoLitri100km(),
            raw.autonomiaKmElettrica(),
            raw.misuraPneumaticiAnteriori(),
            raw.misuraPneumaticiPosteriori(),
            raw.runFlat(),
            raw.prezzoListinoEur(),
            raw.costoTagliandoBaseEur(),
            raw.costoTagliandoMaiorEur(),
            raw.intervalloTagliandoKm(),
            raw.intervalloTagliandoMaiorKm(),
            raw.gruppoAssicurativo()
        );
    }

    private boolean isPlaceholder(String value) {
        if (value == null || value.isBlank()) return true;
        String trimmed = value.trim();
        return trimmed.equalsIgnoreCase("null")
            || (trimmed.startsWith("{") && trimmed.endsWith("}"));
    }

    // -----------------------------------------------
    // TRONCAMENTO TESTO SCRAPING
    // -----------------------------------------------

    /**
     * Tronca il testo di scraping a {@link #MAX_SCRAPING_CHARS} caratteri.
     * <p>
     * Motivazione: Gemini ha un limite sul numero di token di output. Se il prompt
     * (testo scraping + istruzioni) è troppo lungo, il modello raggiunge il limite
     * prima di chiudere il JSON, producendo un {@code MalformedJsonException}
     * del tipo "Unterminated string at path $.nomeMotore".
     * Il testo di auto-data.net contiene già tutti i dati tecnici rilevanti
     * nelle prime sezioni, quindi il troncamento non causa perdita di informazioni.
     * </p>
     */
    private String truncaTestoScraping(String testo) {
        if (testo == null) return "";
        if (testo.length() <= MAX_SCRAPING_CHARS) return testo;
        log.debug("[Orchestratore] Testo scraping troncato da {} a {} chars per evitare MalformedJson da Gemini",
            testo.length(), MAX_SCRAPING_CHARS);
        return testo.substring(0, MAX_SCRAPING_CHARS);
    }

    // -----------------------------------------------
    // WRAPPER AI
    // -----------------------------------------------

    private CarDataDTO callAiSafely(Supplier<CarDataDTO> aiCall) {
        try {
            return aiCall.get();
        } catch (RuntimeException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            if (msg.contains("503") || msg.contains("UNAVAILABLE") || msg.contains("high demand")) {
                log.error("[AI] Gemini 503 dopo tutti i retry: {}", msg);
                throw new GeminiUnavailableException(
                    "Il servizio AI e' temporaneamente sovraccarico. Riprova tra qualche secondo.", ex);
            }
            // MalformedJsonException: Gemini ha restituito JSON troncato.
            // Segnala il problema con un messaggio chiaro invece di propagare
            // un JsonSyntaxException incomprensibile al chiamante.
            if (msg.contains("MalformedJson") || msg.contains("Unterminated string")
                    || msg.contains("JsonSyntaxException") || msg.contains("JsonParseException")) {
                log.error("[AI] Gemini ha restituito JSON malformato/troncato: {}", msg);
                throw new GeminiUnavailableException(
                    "L'AI ha restituito una risposta non valida (JSON troncato). "
                    + "Questo può accadere con testi molto lunghi. Riprova.", ex);
            }
            log.error("[AI] Errore chiamata Gemini: {}", msg);
            throw ex;
        }
    }

    // -----------------------------------------------
    // VALIDAZIONE + DEDUP + PERSIST
    // -----------------------------------------------

    private MotorizzazioneResponseDTO persistiDto(CarDataDTO dto, String fonteDati) {
        log.info("[AI] Estratto: marca='{}' modello='{}' motore='{}' anno={} carburante='{}' kw={}",
            dto.marca(), dto.modello(), dto.nomeMotore(), dto.annoProduzione(),
            dto.tipoCarburante(), dto.potenzaKw());

        if (!dto.isValid()) {
            log.warn("[AI] Dati insufficienti - salvataggio bloccato. "
                   + "DTO: marca='{}' modello='{}' kw={} carburante='{}'.",
                dto.marca(), dto.modello(), dto.potenzaKw(), dto.tipoCarburante());
            throw new IllegalStateException(
                "L'AI non ha estratto i campi minimi obbligatori. "
                + "Controlla che marca/modello/anno siano corretti."
            );
        }

        List<Motorizzazione> esistenti = motorizzazioneRepository
            .findByMarcaModelloAnno(dto.marca(), dto.modello(), dto.annoProduzione());
        if (!esistenti.isEmpty()) {
            Optional<Motorizzazione> dedup = esistenti.stream()
                .filter(m -> m.getNomeMotore() != null
                    && !isPlaceholder(m.getNomeMotore())
                    && (dto.nomeMotore().equalsIgnoreCase(m.getNomeMotore())
                        || dto.nomeMotore().toLowerCase().contains(m.getNomeMotore().toLowerCase())
                        || m.getNomeMotore().toLowerCase().contains(dto.nomeMotore().toLowerCase())))
                .findFirst();
            if (dedup.isPresent()) {
                log.info("[Orchestratore] Dedup per motore: id={} nome='{}'",
                    dedup.get().getId(), dedup.get().getNomeMotore());
                return carDataMapper.toResponseDTO(dedup.get());
            }
            log.info("[Orchestratore] Stessa marca/modello/anno ma motore diverso: procedo con nuovo salvataggio.");
        }

        Marca marca = marcaRepository.findByNomeIgnoreCase(dto.marca())
            .orElseGet(() -> {
                log.info("[DB] Creazione nuova marca: {}", dto.marca());
                return marcaRepository.save(
                    Marca.builder().nome(capitalizza(dto.marca())).build());
            });

        Modello modello = modelloRepository
            .findByMarcaIdAndNomeIgnoreCase(marca.getId(), dto.modello())
            .orElseGet(() -> {
                log.info("[DB] Creazione nuovo modello: {} {}", dto.marca(), dto.modello());
                return modelloRepository.save(
                    Modello.builder()
                        .marca(marca)
                        .nome(capitalizza(dto.modello()))
                        .annoInizio(dto.annoProduzione())
                        .build());
            });

        Motorizzazione motorizzazione = carDataMapper.toEntity(dto, modello);
        motorizzazione.setFonteDati(fonteDati);
        Motorizzazione salvata = motorizzazioneRepository.save(motorizzazione);

        log.info("[DB] Nuova motorizzazione salvata id={} (fonte: {})",
            salvata.getId(), fonteDati);
        return carDataMapper.toResponseDTO(salvata);
    }

    private String capitalizza(String nome) {
        if (nome == null || nome.isBlank()) return nome;
        String t = nome.trim();
        return Character.toUpperCase(t.charAt(0)) + t.substring(1).toLowerCase();
    }
}

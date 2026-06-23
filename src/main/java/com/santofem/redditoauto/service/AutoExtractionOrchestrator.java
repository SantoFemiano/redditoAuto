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
import com.santofem.redditoauto.scraper.MultiSiteScraperResult;
import com.santofem.redditoauto.scraper.ScraperResult;
import com.santofem.redditoauto.scraper.UrlScraperDispatcher;
import com.santofem.redditoauto.scraper.WebScraper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutoExtractionOrchestrator {

    /**
     * Numero massimo di caratteri del testo scraping inviato a Gemini.
     * Valori più alti aumentano il rischio di output token troncati (MalformedJsonException).
     */
    private static final int MAX_SCRAPING_CHARS = 4500;

    private final WebScraper webScraper;
    private final UrlScraperDispatcher urlScraperDispatcher;
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
            int annoEffettivo = scraperResult.annoEffettivo();
            warningAnno = scraperResult.buildWarningAnno();

            if (scraperResult.annoFallback()) {
                log.warn("[Orchestratore] Anno richiesto={} non disponibile: salvataggio con anno effettivo={}",
                    anno, annoEffettivo);
            }

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

        MotorizzazioneResponseDTO response = persistiDto(dto, fonteDati, scraperResult.annoFrom(), scraperResult.annoTo());
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
    // ENDPOINT: /estrai-url  (ora passa per il Dispatcher multi-site)
    // -----------------------------------------------

    /**
     * Estrae i dati tecnici da un URL diretto.
     *
     * Il flusso è:
     * 1. UrlScraperDispatcher riconosce il sito e delega allo scraper specializzato
     * 2. Lo scraper restituisce testo tecnico + prezzo (se disponibile) + hint identità
     * 3. Gli hint vengono usati per guidare Gemini nell'estrazione strutturata
     * 4. Se il prezzo è stato trovato dallo scraper, viene propagato nel DTO di risposta
     *    sovrascrivendo l'eventuale prezzo estratto dall'AI (più affidabile perché è un
     *    valore numerico già parsato, non un'inferenza del modello)
     */
    @Transactional
    public MotorizzazioneResponseDTO estraiDaUrl(String url, String fonteDatiInput) {
        log.info("[Orchestratore] Estrazione da URL: {}", url);

        // 1. Dispatching al sito corretto
        MultiSiteScraperResult scraped = urlScraperDispatcher.dispatch(url);

        if (!scraped.hasTesto()) {
            log.error("[Orchestratore] Nessun testo estratto dall'URL: {}", url);
            throw new IllegalStateException("Impossibile estrarre testo dall'URL: " + url);
        }

        // 2. Costruisce il contesto di hint per guidare l'AI
        String marca   = scraped.getMarcaHint()   != null ? scraped.getMarcaHint()   : "sconosciuta";
        String modello = scraped.getModelloHint() != null ? scraped.getModelloHint() : "sconosciuto";
        String annoStr = scraped.getAnnoHint()    > 0     ? String.valueOf(scraped.getAnnoHint()) : "0";

        log.info("[Orchestratore] URL scraping riuscito da '{}': {} chars, prezzo={} EUR, hint: {} {} {}",
                scraped.getSiteNome(), scraped.getTesto().length(),
                scraped.getPrezzoEur(), marca, modello, annoStr);

        // 3. Tronca il testo per evitare JSON troncato da Gemini
        String testoScraping = truncaTestoScraping(scraped.getTesto());

        // 4. Estrazione AI guidata dagli hint identità
        CarDataDTO dto = callAiSafely(() -> aiExtractor.extractCarData(
                marca, modello, "sconosciuto", annoStr, testoScraping));

        // 5. Fonte dati reale dal sito riconosciuto
        String fonteDati = "scraping:" + scraped.getSiteNome() + ":" + url;

        // 6. Persisti e ottieni il DTO di risposta
        // Se lo scraper ha fornito un hint anno, usalo come from/to
        Integer hintFrom = scraped.getAnnoHint() > 0 ? scraped.getAnnoHint() : null;
        Integer hintTo = hintFrom;
        MotorizzazioneResponseDTO response = persistiDto(dto, fonteDati, hintFrom, hintTo);

        // 7. Propaga il prezzo trovato dallo scraper (più affidabile dell'AI)
        //    Solo se il prezzo è presente e nel range plausibile per un'auto
        if (scraped.hasPrezzo() && scraped.getPrezzoEur() >= 1000) {
            log.info("[Orchestratore] Prezzo scraper ({} EUR, tipo={}) propagato nella risposta",
                    scraped.getPrezzoEur(), scraped.getTipoPrezzo());
            response.setPrezzoListinoEur(scraped.getPrezzoEur());
            response.setFonteScraping(scraped.getSiteNome());
        }

        return response;
    }

    // -----------------------------------------------
    // ENDPOINT: /estrai (testo grezzo)
    // -----------------------------------------------

    @Transactional
    public MotorizzazioneResponseDTO estraiDaTesto(String testoGrezzo, String fonteDati) {
        CarDataDTO dto = callAiSafely(() -> aiExtractor.extractCarData(
            "sconosciuta", "sconosciuto", "sconosciuto", "0", truncaTestoScraping(testoGrezzo)));
        return persistiDto(dto, fonteDati, null, null);
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
            marca, modello, motore, anno, raw.annoFineProduzione(),
            raw.annoProduzioneM(),raw.annoFineProduzioneM(),
            raw.tipoCarburante(), raw.tipoCambio(),
            raw.potenzaKw(), raw.potenzaCv(), raw.cilindrataCC(),
            raw.consumoMedioLitri100km(), raw.consumoUrbanoLitri100km(), raw.consumoExtraurbanoLitri100km(),
            raw.autonomiaKmElettrica(),
            raw.misuraPneumaticiAnteriori(), raw.misuraPneumaticiPosteriori(), raw.runFlat(),
            raw.kmDurataPneumatici(), raw.prezzoListinoEur(), raw.costoTagliandoBaseEur(), raw.costoTagliandoMaiorEur()
            ,raw.intervalloTagliandoKm(), raw.intervalloTagliandoMaiorKm(),
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

    private MotorizzazioneResponseDTO persistiDto(CarDataDTO dto, String fonteDati, Integer annoFrom, Integer annoTo) {
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

        Modello modello;
        var modelloOpt = modelloRepository.findByMarcaIdAndNomeIgnoreCase(marca.getId(), dto.modello());
        if (modelloOpt.isPresent()) {
            modello = modelloOpt.get();
            boolean updated = false;
            // Aggiorna annoInizio se fornito dallo scraper e mancante in DB
            if (annoFrom != null) {
                if (modello.getAnnoInizio() == null || !modello.getAnnoInizio().equals(annoFrom)) {
                    modello.setAnnoInizio(annoFrom);
                    updated = true;
                }
            }
            // Aggiorna annoFine se fornito dallo scraper
            if (annoTo != null) {
                Integer newAnnoFine = (annoTo >= 2099) ? null : annoTo;
                if (!Objects.equals(modello.getAnnoFine(), newAnnoFine)) {
                    modello.setAnnoFine(newAnnoFine);
                    updated = true;
                }
            }
            if (updated) {
                log.info("[DB] Aggiornamento modello esistente '{}' {} con annoFrom={} annoTo={}",
                    marca.getNome(), modello.getNome(), annoFrom, annoTo);
                modello = modelloRepository.save(modello);
            }
        } else {
            log.info("[DB] Creazione nuovo modello: {} {}", dto.marca(), dto.modello());
            Modello.ModelloBuilder builder = Modello.builder()
                    .marca(marca)
                    .nome(capitalizza(dto.modello()))
                    // FIX: Forza l'uso dell'annoFrom dello scraper per l'entità Modello
                    .annoInizio(annoFrom != null ? annoFrom : dto.annoProduzione());

            if (annoTo != null) {
                if (annoTo >= 2099) builder.annoFine(null);
                else builder.annoFine(annoTo);
            }

            modello = modelloRepository.save(builder.build());
        }

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

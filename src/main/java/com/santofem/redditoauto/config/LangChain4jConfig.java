package com.santofem.redditoauto.config;

import com.santofem.redditoauto.ai.AiCarDataExtractor;
import com.santofem.redditoauto.ai.AiDirectDataProvider;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configurazione Spring per LangChain4j + Google Gemini.
 *
 * PROFILO:
 * - Attiva su tutti i profili TRANNE 'test'.
 *   Nei test il Bean non viene creato per evitare chiamate reali all'API Gemini
 *   e per non fallire l'avvio con la fake key 'test-fake-key'.
 *   I test unitari del service usano @Mock su AiCarDataExtractor direttamente.
 *
 * MODELLO SCELTO: gemini-2.5-flash
 * - Veloce, economico, ottimo per structured output / extraction tasks.
 * - temperature=0.0: risposta deterministica, zero creativita'.
 *   Fondamentale per un extractor: vogliamo mapping, non generazione.
 *
 * STRUCTURED OUTPUT:
 * - responseFormat(ResponseFormat.JSON) forza Gemini 2.x a rispondere
 *   sempre in JSON valido, prevenendo risposte in testo libero.
 * - LangChain4j invia automaticamente il JSON Schema di CarDataDTO a Gemini
 *   tramite il parametro 'responseSchema' dell'API.
 *
 * AI SERVICES REGISTRATI:
 * - AiCarDataExtractor: estrae dati tecnici DA un testo grezzo (scraping)
 * - AiDirectDataProvider: genera dati tecnici DIRETTAMENTE dal training set
 *   di Gemini, usato come fallback quando lo scraping fallisce.
 *   Entrambi usano lo stesso ChatModel (stessa chiave, stesso modello).
 */
@Configuration
@Profile("!test")
public class LangChain4jConfig {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    /**
     * Configura il modello Gemini.
     * temperature=0.0 e' critico: garantisce output deterministico
     * e previene le allucinazioni nell'extraction task.
     * responseFormat(ResponseFormat.JSON) e' obbligatorio con Gemini 2.x
     * per forzare JSON puro invece di testo libero.
     */
    @Bean
    @ConditionalOnProperty(name = "gemini.api.key", havingValue = "changeme", matchIfMissing = false)
    public GoogleAiGeminiChatModel geminiChatModelStub() {
        throw new IllegalStateException(
            "[RedditoAuto] GEMINI_API_KEY non configurata! " +
            "Esporta la variabile d'ambiente: export GEMINI_API_KEY=la_tua_chiave");
    }

    @Bean
    @ConditionalOnMissingBean(GoogleAiGeminiChatModel.class)
    public GoogleAiGeminiChatModel geminiChatModel() {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(geminiApiKey)
                .modelName("gemini-2.5-flash")
                .temperature(0.0)
                .maxOutputTokens(2048)
                .responseFormat(ResponseFormat.JSON)
                .build();
    }

    @Bean
    public AiCarDataExtractor aiCarDataExtractor(GoogleAiGeminiChatModel chatModel) {
        return AiServices.builder(AiCarDataExtractor.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    @Bean
    public AiDirectDataProvider aiDirectDataProvider(GoogleAiGeminiChatModel chatModel) {
        return AiServices.builder(AiDirectDataProvider.class)
                .chatLanguageModel(chatModel)
                .build();
    }
}

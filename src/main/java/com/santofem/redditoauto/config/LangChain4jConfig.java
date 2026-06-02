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
 *
 * MODELLO: gemini-2.5-flash
 * - Veloce, economico, ottimo per structured output / extraction tasks.
 * - temperature=0.0: risposta deterministica.
 *
 * OUTPUT TOKENS:
 * - 4096 tokens: sufficiente per un JSON con ~30 campi + stringhe descrittive.
 *   Il JSON CarDataDTO ha ~25 campi, il 90% numerici. Raramente supera 500 tokens.
 *   Con 4096 si elimina il rischio di JSON troncato.
 *
 * RETRY:
 * - maxRetries(5) con backoff esponenziale gestito da LangChain4j.
 */
@Configuration
@Profile("!test")
public class LangChain4jConfig {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

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
                .maxOutputTokens(4096)  // aumentato: JSON CarDataDTO non viene piu' troncato
                .responseFormat(ResponseFormat.JSON)
                .maxRetries(5)
                .logRequestsAndResponses(false)
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

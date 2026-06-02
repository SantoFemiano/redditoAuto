package com.santofem.redditoauto.config;

import com.santofem.redditoauto.ai.AiCarDataExtractor;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LangChain4jConfig {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Bean
    public GoogleAiGeminiChatModel geminiChatModel() {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(geminiApiKey)
                .modelName("gemini-1.5-flash") // Veloce ed economico per extraction tasks
                .temperature(0.0)              // Temperatura 0 = massimo determinismo, zero creatività
                .maxOutputTokens(2048)
                .build();
    }

    @Bean
    public AiCarDataExtractor aiCarDataExtractor(GoogleAiGeminiChatModel chatModel) {
        return AiServices.builder(AiCarDataExtractor.class)
                .chatLanguageModel(chatModel)
                .build();
    }
}

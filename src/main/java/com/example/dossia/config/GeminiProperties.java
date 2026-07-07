package com.example.dossia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dossia.gemini")
public record GeminiProperties(
        String apiKey,
        String chatModel,
        String embeddingModel,
        int retrievalLimit,
        boolean enabled) {

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}

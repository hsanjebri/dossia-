package com.example.dossia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dossia.ollama")
public record OllamaProperties(
        boolean enabled,
        String baseUrl,
        String chatModel,
        String embeddingModel) {

    public boolean isConfigured() {
        return enabled && baseUrl != null && !baseUrl.isBlank() && chatModel != null && !chatModel.isBlank();
    }

    public boolean isEmbedConfigured() {
        return isConfigured() && embeddingModel != null && !embeddingModel.isBlank();
    }
}

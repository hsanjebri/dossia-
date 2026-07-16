package com.example.dossia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which backend answers chat / embeddings.
 * <ul>
 *   <li>{@code auto} — Gemini if configured, else Ollama; Gemini failures fall back to Ollama</li>
 *   <li>{@code gemini} — Gemini only</li>
 *   <li>{@code ollama} — Ollama / OpenAI-compatible only</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "dossia.llm")
public record LlmProperties(String provider) {

    public String normalizedProvider() {
        if (provider == null || provider.isBlank()) {
            return "auto";
        }
        return provider.strip().toLowerCase();
    }
}

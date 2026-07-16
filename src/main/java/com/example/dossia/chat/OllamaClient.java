package com.example.dossia.chat;

import com.example.dossia.common.GeminiException;
import com.example.dossia.config.OllamaProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * OpenAI-compatible client for Ollama ({@code /v1/chat/completions}, {@code /v1/embeddings}).
 */
@Component
public class OllamaClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final OllamaProperties properties;

    public OllamaClient(@Qualifier("ollamaRestClient") RestClient ollamaRestClient, OllamaProperties properties) {
        this.restClient = ollamaRestClient;
        this.properties = properties;
    }

    public String generate(
            String systemPrompt, List<ConversationTurn> history, String userPrompt, double temperature) {
        requireChatConfigured();

        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        for (ConversationTurn turn : history) {
            String role = "model".equalsIgnoreCase(turn.role()) || "assistant".equalsIgnoreCase(turn.role())
                    ? "assistant"
                    : "user";
            messages.add(Map.of("role", role, "content", turn.content()));
        }
        messages.add(Map.of("role", "user", "content", userPrompt));

        Map<String, Object> body = new HashMap<>();
        body.put("model", properties.chatModel());
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("stream", false);

        try {
            Map<String, Object> response = restClient
                    .post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MAP_TYPE);

            String answer = extractChatContent(response);
            if (answer == null || answer.isBlank()) {
                throw new GeminiException("Ollama returned an empty answer");
            }
            return answer.strip();
        } catch (RestClientResponseException ex) {
            throw new GeminiException("Ollama chat failed: " + summarizeError(ex), ex);
        }
    }

    public float[] embed(String text) {
        requireEmbedConfigured();

        Map<String, Object> body = Map.of(
                "model", properties.embeddingModel(),
                "input", text == null ? "" : text);

        try {
            Map<String, Object> response = restClient
                    .post()
                    .uri("/v1/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MAP_TYPE);

            if (response == null) {
                throw new GeminiException("Ollama embedding returned empty response");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            if (data == null || data.isEmpty()) {
                throw new GeminiException("Ollama embedding response missing data");
            }

            @SuppressWarnings("unchecked")
            List<Number> values = (List<Number>) data.get(0).get("embedding");
            if (values == null || values.isEmpty()) {
                throw new GeminiException("Ollama embedding response missing embedding values");
            }

            float[] result = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                result[i] = values.get(i).floatValue();
            }
            return result;
        } catch (RestClientResponseException ex) {
            throw new GeminiException("Ollama embedding failed: " + summarizeError(ex), ex);
        }
    }

    private String extractChatContent(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) {
            return null;
        }
        Object content = message.get("content");
        return content == null ? null : String.valueOf(content);
    }

    private void requireChatConfigured() {
        if (!properties.isConfigured()) {
            throw new GeminiException(
                    "Ollama is not configured. Set OLLAMA_ENABLED=true and start the ollama container.");
        }
    }

    private void requireEmbedConfigured() {
        if (!properties.isEmbedConfigured()) {
            throw new GeminiException(
                    "Ollama embeddings not configured. Set OLLAMA_EMBEDDING_MODEL (e.g. nomic-embed-text).");
        }
    }

    private String summarizeError(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body != null && !body.isBlank()) {
            return body.length() > 300 ? body.substring(0, 300) : body;
        }
        return ex.getStatusCode() + " " + ex.getStatusText();
    }
}

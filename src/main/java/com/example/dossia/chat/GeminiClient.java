package com.example.dossia.chat;

import com.example.dossia.common.GeminiException;
import com.example.dossia.config.GeminiProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GeminiClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final GeminiProperties properties;

    public GeminiClient(RestClient geminiRestClient, GeminiProperties properties) {
        this.restClient = geminiRestClient;
        this.properties = properties;
    }

    public float[] embed(String text) {
        requireConfigured();
        String model = properties.embeddingModel();
        Map<String, Object> body = Map.of(
                "model", "models/" + model,
                "content", Map.of("parts", List.of(Map.of("text", text))),
                "outputDimensionality", 768);

        try {
            Map<String, Object> response = restClient
                    .post()
                    .uri("/v1beta/models/{model}:embedContent", model)
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MAP_TYPE);

            if (response == null) {
                throw new GeminiException("Gemini embedding returned empty response");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> embedding = (Map<String, Object>) response.get("embedding");
            if (embedding == null) {
                throw new GeminiException("Gemini embedding response missing embedding");
            }

            @SuppressWarnings("unchecked")
            List<Number> values = (List<Number>) embedding.get("values");
            if (values == null || values.isEmpty()) {
                throw new GeminiException("Gemini embedding response missing values");
            }

            float[] result = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                result[i] = values.get(i).floatValue();
            }
            return result;
        } catch (RestClientResponseException ex) {
            throw new GeminiException("Gemini embedding failed: " + summarizeError(ex), ex);
        }
    }

    public String generate(String systemPrompt, String userPrompt) {
        return generate(systemPrompt, List.of(), userPrompt, 0.2);
    }

    public String generate(String systemPrompt, String userPrompt, double temperature) {
        return generate(systemPrompt, List.of(), userPrompt, temperature);
    }

    public String generate(
            String systemPrompt, List<ConversationTurn> history, String userPrompt, double temperature) {
        requireConfigured();
        String model = properties.chatModel();

        List<Map<String, Object>> contents = new ArrayList<>();
        for (ConversationTurn turn : history) {
            contents.add(Map.of("role", turn.role(), "parts", List.of(Map.of("text", turn.content()))));
        }
        contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt))));

        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", contents,
                "generationConfig", Map.of(
                        "temperature", temperature,
                        "maxOutputTokens", 2048,
                        "thinkingConfig", Map.of("thinkingBudget", 0)));

        try {
            Map<String, Object> response = restClient
                    .post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MAP_TYPE);

            return extractAnswer(response);
        } catch (RestClientResponseException ex) {
            throw new GeminiException("Gemini chat failed: " + summarizeError(ex), ex);
        }
    }

    private String extractAnswer(Map<String, Object> response) {
        if (response == null) {
            throw new GeminiException("Gemini chat returned empty response");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new GeminiException("Gemini chat response missing candidates");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        if (content == null) {
            throw new GeminiException("Gemini chat response missing content");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty()) {
            throw new GeminiException("Gemini chat response missing parts");
        }

        StringBuilder answer = new StringBuilder();
        for (Map<String, Object> part : parts) {
            Object text = part.get("text");
            if (text != null) {
                answer.append(text);
            }
        }

        if (answer.isEmpty()) {
            throw new GeminiException("Gemini returned an empty answer");
        }
        return answer.toString().trim();
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new GeminiException("Gemini is not configured. Set GEMINI_API_KEY in .env");
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

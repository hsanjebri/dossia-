package com.example.dossia.chat;

import com.example.dossia.common.GeminiException;
import com.example.dossia.config.GeminiProperties;
import com.example.dossia.config.LlmProperties;
import com.example.dossia.config.OllamaProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Routes chat + embeddings across Gemini and Ollama (OpenAI-compatible).
 */
@Component
public class LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(LlmGateway.class);

    private final LlmProperties llmProperties;
    private final GeminiProperties geminiProperties;
    private final OllamaProperties ollamaProperties;
    private final GeminiClient geminiClient;
    private final OllamaClient ollamaClient;

    public LlmGateway(
            LlmProperties llmProperties,
            GeminiProperties geminiProperties,
            OllamaProperties ollamaProperties,
            GeminiClient geminiClient,
            OllamaClient ollamaClient) {
        this.llmProperties = llmProperties;
        this.geminiProperties = geminiProperties;
        this.ollamaProperties = ollamaProperties;
        this.geminiClient = geminiClient;
        this.ollamaClient = ollamaClient;
    }

    public boolean isChatConfigured() {
        return geminiProperties.isConfigured() || ollamaProperties.isConfigured();
    }

    public boolean isEmbedConfigured() {
        return geminiProperties.isConfigured() || ollamaProperties.isEmbedConfigured();
    }

    public LlmText generate(
            String systemPrompt, List<ConversationTurn> history, String userPrompt, double temperature) {
        String provider = llmProperties.normalizedProvider();
        return switch (provider) {
            case "gemini" -> {
                requireGeminiChat();
                yield new LlmText(
                        geminiClient.generate(systemPrompt, history, userPrompt, temperature),
                        geminiProperties.chatModel());
            }
            case "ollama" -> {
                requireOllamaChat();
                yield new LlmText(
                        ollamaClient.generate(systemPrompt, history, userPrompt, temperature),
                        ollamaProperties.chatModel());
            }
            default -> generateAuto(systemPrompt, history, userPrompt, temperature);
        };
    }

    public float[] embed(String text) {
        String provider = llmProperties.normalizedProvider();
        return switch (provider) {
            case "gemini" -> {
                requireGeminiEmbed();
                yield geminiClient.embed(text);
            }
            case "ollama" -> {
                requireOllamaEmbed();
                yield ollamaClient.embed(text);
            }
            default -> embedAuto(text);
        };
    }

    private LlmText generateAuto(
            String systemPrompt, List<ConversationTurn> history, String userPrompt, double temperature) {
        if (geminiProperties.isConfigured()) {
            try {
                return new LlmText(
                        geminiClient.generate(systemPrompt, history, userPrompt, temperature),
                        geminiProperties.chatModel());
            } catch (GeminiException ex) {
                if (!ollamaProperties.isConfigured()) {
                    throw ex;
                }
                log.warn("Gemini chat failed ({}), falling back to Ollama {}", ex.getMessage(), ollamaProperties.chatModel());
            }
        }
        if (ollamaProperties.isConfigured()) {
            return new LlmText(
                    ollamaClient.generate(systemPrompt, history, userPrompt, temperature),
                    ollamaProperties.chatModel());
        }
        throw new GeminiException(
                "No LLM configured. Set GEMINI_API_KEY or enable Ollama (OLLAMA_ENABLED=true).");
    }

    private float[] embedAuto(String text) {
        if (geminiProperties.isConfigured()) {
            try {
                return geminiClient.embed(text);
            } catch (GeminiException ex) {
                if (!ollamaProperties.isEmbedConfigured()) {
                    throw ex;
                }
                log.warn("Gemini embed failed ({}), falling back to Ollama {}", ex.getMessage(), ollamaProperties.embeddingModel());
            }
        }
        if (ollamaProperties.isEmbedConfigured()) {
            return ollamaClient.embed(text);
        }
        throw new GeminiException(
                "No embedding backend configured. Set GEMINI_API_KEY or OLLAMA_EMBEDDING_MODEL.");
    }

    private void requireGeminiChat() {
        if (!geminiProperties.isConfigured()) {
            throw new GeminiException("Gemini is not configured. Set GEMINI_API_KEY in .env");
        }
    }

    private void requireGeminiEmbed() {
        requireGeminiChat();
    }

    private void requireOllamaChat() {
        if (!ollamaProperties.isConfigured()) {
            throw new GeminiException("Ollama is not configured. Set OLLAMA_ENABLED=true and start ollama.");
        }
    }

    private void requireOllamaEmbed() {
        if (!ollamaProperties.isEmbedConfigured()) {
            throw new GeminiException("Ollama embeddings not configured.");
        }
    }

    public record LlmText(String text, String model) {}
}

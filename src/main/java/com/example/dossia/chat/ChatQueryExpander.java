package com.example.dossia.chat;

import com.example.dossia.common.GeminiException;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ChatQueryExpander {

    private static final String SYSTEM_PROMPT =
            """
            Tu reformules des questions utilisateur en requêtes de recherche pour des procédures
            administratives tunisiennes (Dosya / دوسيا).

            Règles:
            - Réponds UNIQUEMENT avec la reformulation (1 à 2 phrases courtes).
            - Conserve les mots-clés importants : diplôme, ingénieur, équivalence, passeport, CIN,
              timbre fiscal, permis, naissance, mariage, nationalité, etc.
            - Si la question est déjà claire, renvoie-la telle quelle.
            - Pas de guillemets, pas d'explication.
            """;

    public Optional<String> expand(LlmGateway llmGateway, String message) {
        if (message == null || message.strip().length() < 8 || !llmGateway.isChatConfigured()) {
            return Optional.empty();
        }

        String trimmed = message.strip();
        try {
            String expanded = llmGateway
                    .generate(SYSTEM_PROMPT, java.util.List.of(), trimmed, 0.1)
                    .text();
            if (expanded.isBlank()) {
                return Optional.empty();
            }

            String normalized = expanded.strip();
            if (normalized.equalsIgnoreCase(trimmed)) {
                return Optional.empty();
            }
            return Optional.of(normalized);
        } catch (GeminiException ignored) {
            return Optional.empty();
        }
    }
}

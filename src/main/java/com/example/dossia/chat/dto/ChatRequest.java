package com.example.dossia.chat.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record ChatRequest(
        @NotBlank String message, UUID sessionId, Double latitude, Double longitude) {

    public ChatRequest(String message) {
        this(message, null, null, null);
    }
}

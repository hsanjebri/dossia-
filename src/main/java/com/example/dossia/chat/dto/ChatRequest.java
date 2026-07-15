package com.example.dossia.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public record ChatRequest(
        @NotBlank String message,
        UUID sessionId,
        Double latitude,
        Double longitude,
        List<@Valid ChatHistoryTurnDto> history) {

    public ChatRequest(String message) {
        this(message, null, null, null, null);
    }

    public ChatRequest(String message, UUID sessionId, Double latitude, Double longitude) {
        this(message, sessionId, latitude, longitude, null);
    }
}

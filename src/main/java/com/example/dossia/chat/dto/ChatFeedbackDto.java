package com.example.dossia.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatFeedbackDto(
        UUID id,
        UUID userId,
        UUID sessionId,
        String userMessage,
        String assistantAnswer,
        String reason,
        String clientIp,
        String status,
        Instant createdAt) {}

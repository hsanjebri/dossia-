package com.example.dossia.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ChatFeedbackRequest(
        UUID sessionId,
        @Size(max = 4000) String userMessage,
        @Size(max = 8000) String assistantAnswer,
        @NotBlank @Size(max = 500) String reason) {}

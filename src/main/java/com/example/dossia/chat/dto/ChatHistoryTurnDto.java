package com.example.dossia.chat.dto;

import jakarta.validation.constraints.NotBlank;

/** One prior turn sent by the client (guest multi-turn context). */
public record ChatHistoryTurnDto(@NotBlank String role, @NotBlank String content) {}

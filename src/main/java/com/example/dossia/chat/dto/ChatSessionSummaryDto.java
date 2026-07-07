package com.example.dossia.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatSessionSummaryDto(UUID id, String title, String preview, Instant updatedAt) {}

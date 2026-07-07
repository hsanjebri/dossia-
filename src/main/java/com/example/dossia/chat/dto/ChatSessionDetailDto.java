package com.example.dossia.chat.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatSessionDetailDto(UUID id, String title, Instant updatedAt, List<StoredChatMessageDto> messages) {}

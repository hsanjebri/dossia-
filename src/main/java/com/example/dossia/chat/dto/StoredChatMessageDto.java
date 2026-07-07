package com.example.dossia.chat.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StoredChatMessageDto(
        UUID id, String role, String content, List<ChatSourceDto> sources, Instant createdAt) {}

package com.example.dossia.chat.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatSourceDto(
        UUID id,
        String slug,
        String title,
        String sourceUrl,
        Instant lastVerifiedAt,
        double similarityScore) {}

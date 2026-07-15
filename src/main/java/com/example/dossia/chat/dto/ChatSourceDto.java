package com.example.dossia.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatSourceDto(
        UUID id,
        String slug,
        String title,
        String sourceUrl,
        Instant lastVerifiedAt,
        double similarityScore,
        String ministry,
        String category) {

    /** Back-compat helper without ministry/category. */
    public ChatSourceDto(
            UUID id,
            String slug,
            String title,
            String sourceUrl,
            Instant lastVerifiedAt,
            double similarityScore) {
        this(id, slug, title, sourceUrl, lastVerifiedAt, similarityScore, null, null);
    }
}

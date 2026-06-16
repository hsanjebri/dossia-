package com.example.dossia.procedure.dto;

import com.example.dossia.procedure.domain.Difficulty;
import com.example.dossia.procedure.domain.ProcedureCategory;
import java.time.Instant;
import java.util.UUID;

public record ProcedureSummaryDto(
        UUID id,
        String slug,
        String title,
        String titleAr,
        String ministry,
        ProcedureCategory category,
        Difficulty difficulty,
        String deliveryMode,
        String processingTime,
        String fees,
        Instant lastVerifiedAt) {}

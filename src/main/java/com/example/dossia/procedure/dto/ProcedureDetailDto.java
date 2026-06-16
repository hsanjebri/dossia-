package com.example.dossia.procedure.dto;

import com.example.dossia.procedure.domain.Difficulty;
import com.example.dossia.procedure.domain.ProcedureCategory;
import com.example.dossia.procedure.domain.ProcedureStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProcedureDetailDto(
        UUID id,
        String slug,
        String title,
        String titleAr,
        String titleTn,
        String description,
        String descriptionAr,
        String ministry,
        ProcedureCategory category,
        Difficulty difficulty,
        String deliveryMode,
        String processingTime,
        String fees,
        String sourceUrl,
        String sourceReference,
        Instant lastVerifiedAt,
        ProcedureStatus status,
        List<ProcedureDocumentDto> documents,
        List<ProcedureStepDto> steps,
        List<OfficeLocationDto> offices,
        List<ProcedureSummaryDto> relatedProcedures) {}

package com.example.dossia.procedure.dto;

import com.example.dossia.procedure.domain.Difficulty;
import com.example.dossia.procedure.domain.ProcedureCategory;
import com.example.dossia.procedure.domain.ProcedureStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateProcedureRequest(
        String slug,
        @NotBlank String titleFr,
        @NotBlank String titleAr,
        String titleTn,
        String descriptionFr,
        String descriptionAr,
        @NotBlank String ministry,
        @NotNull ProcedureCategory category,
        @NotNull Difficulty difficulty,
        String deliveryMode,
        String processingTime,
        String fees,
        String sourceUrl,
        String sourceReference,
        ProcedureStatus status,
        @Valid List<ProcedureDocumentRequest> documents,
        @Valid List<ProcedureStepRequest> steps,
        @Valid List<OfficeLocationRequest> offices,
        List<String> relatedProcedureSlugs) {}

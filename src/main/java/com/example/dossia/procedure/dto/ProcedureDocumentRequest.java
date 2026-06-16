package com.example.dossia.procedure.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProcedureDocumentRequest(
        @NotNull Integer sortOrder,
        @NotBlank String titleFr,
        @NotBlank String titleAr,
        String descriptionFr,
        String descriptionAr) {}

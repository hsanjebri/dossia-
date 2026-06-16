package com.example.dossia.procedure.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProcedureStepRequest(
        @NotNull Integer stepNumber,
        @NotBlank String titleFr,
        @NotBlank String titleAr,
        String descriptionFr,
        String descriptionAr) {}

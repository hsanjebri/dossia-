package com.example.dossia.procedure.dto;

public record ProcedureDocumentDto(
        String title,
        String titleAr,
        String description,
        String descriptionAr,
        int sortOrder) {}

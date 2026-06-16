package com.example.dossia.procedure.dto;

import jakarta.validation.constraints.NotBlank;

public record OfficeLocationRequest(
        @NotBlank String name,
        @NotBlank String address,
        String city,
        String governorate,
        String hoursFr,
        String hoursAr,
        Double latitude,
        Double longitude) {}

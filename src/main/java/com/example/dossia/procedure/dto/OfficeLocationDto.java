package com.example.dossia.procedure.dto;

public record OfficeLocationDto(
        String name,
        String address,
        String city,
        String governorate,
        String hours,
        String hoursAr,
        Double latitude,
        Double longitude) {}

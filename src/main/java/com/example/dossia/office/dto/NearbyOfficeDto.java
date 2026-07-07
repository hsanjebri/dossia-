package com.example.dossia.office.dto;

import java.util.UUID;

public record NearbyOfficeDto(
        UUID id,
        String name,
        String officeType,
        String address,
        String city,
        String governorate,
        String hours,
        double latitude,
        double longitude,
        double distanceKm,
        String procedureSlug,
        String procedureTitle,
        String mapsUrl,
        String routeUrl) {}

package com.example.dossia.chat.dto;

import com.example.dossia.office.dto.NearbyOfficeDto;
import java.util.List;
import java.util.UUID;

public record ChatResponse(
        String answer,
        List<ChatSourceDto> sources,
        String model,
        UUID sessionId,
        List<NearbyOfficeDto> nearbyOffices) {

    public ChatResponse(String answer, List<ChatSourceDto> sources, String model) {
        this(answer, sources, model, null, List.of());
    }

    public ChatResponse(String answer, List<ChatSourceDto> sources, String model, UUID sessionId) {
        this(answer, sources, model, sessionId, List.of());
    }
}

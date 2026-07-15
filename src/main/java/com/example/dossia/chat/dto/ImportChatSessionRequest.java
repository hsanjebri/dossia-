package com.example.dossia.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** One guest chat session to import into a registered account. */
public record ImportChatSessionRequest(
        String title, @NotEmpty @Valid List<ImportChatMessageDto> messages) {

    public record ImportChatMessageDto(
            @NotBlank String role, @NotBlank String content, List<ChatSourceDto> sources) {}
}

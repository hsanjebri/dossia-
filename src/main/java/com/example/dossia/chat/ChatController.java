package com.example.dossia.chat;

import com.example.dossia.auth.security.UserPrincipal;
import com.example.dossia.chat.dto.ChatRequest;
import com.example.dossia.chat.dto.ChatResponse;
import com.example.dossia.common.Language;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "fr") String lang) {
        return chatService.chat(request, parseLang(lang), principal);
    }

    private Language parseLang(String lang) {
        try {
            return Language.valueOf(lang.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Language.FR;
        }
    }
}

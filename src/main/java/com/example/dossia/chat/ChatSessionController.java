package com.example.dossia.chat;

import com.example.dossia.auth.security.UserPrincipal;
import com.example.dossia.chat.domain.ChatSession;
import com.example.dossia.chat.dto.ChatRequest;
import com.example.dossia.chat.dto.ChatResponse;
import com.example.dossia.chat.dto.ChatSessionDetailDto;
import com.example.dossia.chat.dto.ChatSessionSummaryDto;
import com.example.dossia.common.Language;
import com.example.dossia.common.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/sessions")
public class ChatSessionController {

    private final ChatHistoryService chatHistoryService;

    public ChatSessionController(ChatHistoryService chatHistoryService) {
        this.chatHistoryService = chatHistoryService;
    }

    @GetMapping
    public List<ChatSessionSummaryDto> list(@AuthenticationPrincipal UserPrincipal principal) {
        requireUser(principal);
        return chatHistoryService.listSessions(principal.getId());
    }

    @GetMapping("/{id}")
    public ChatSessionDetailDto get(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        requireUser(principal);
        return chatHistoryService.getSession(principal.getId(), id);
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        requireUser(principal);
        chatHistoryService.deleteSession(principal.getId(), id);
    }

    private UUID requireUser(UserPrincipal principal) {
        if (principal == null) {
            throw new ResourceNotFoundException("Authentication required");
        }
        return principal.getId();
    }
}

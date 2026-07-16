package com.example.dossia.chat;

import com.example.dossia.chat.dto.ChatFeedbackDto;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/chat/feedback")
public class AdminChatFeedbackController {

    private final ChatFeedbackService feedbackService;

    public AdminChatFeedbackController(ChatFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public Page<ChatFeedbackDto> list(
            @RequestParam(required = false) String status, Pageable pageable) {
        return feedbackService.list(status, pageable);
    }

    @PostMapping("/{id}/reviewed")
    public ChatFeedbackDto markReviewed(@PathVariable UUID id) {
        return feedbackService.markReviewed(id);
    }
}

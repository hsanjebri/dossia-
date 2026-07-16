package com.example.dossia.chat;

import com.example.dossia.auth.security.UserPrincipal;
import com.example.dossia.chat.dto.ChatFeedbackDto;
import com.example.dossia.chat.dto.ChatFeedbackRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/feedback")
public class ChatFeedbackController {

    private final ChatFeedbackService feedbackService;

    public ChatFeedbackController(ChatFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public ChatFeedbackDto submit(
            @Valid @RequestBody ChatFeedbackRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        return feedbackService.submit(request, principal, clientIp(httpRequest));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

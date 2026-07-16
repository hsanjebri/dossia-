package com.example.dossia.chat;

import com.example.dossia.auth.security.UserPrincipal;
import com.example.dossia.chat.domain.ChatFeedback;
import com.example.dossia.chat.dto.ChatFeedbackDto;
import com.example.dossia.chat.dto.ChatFeedbackRequest;
import com.example.dossia.chat.repository.ChatFeedbackRepository;
import com.example.dossia.common.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatFeedbackService {

    private final ChatFeedbackRepository feedbackRepository;

    public ChatFeedbackService(ChatFeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional
    public ChatFeedbackDto submit(ChatFeedbackRequest request, UserPrincipal principal, String clientIp) {
        ChatFeedback feedback = new ChatFeedback();
        if (principal != null) {
            feedback.setUserId(principal.getId());
        }
        feedback.setSessionId(request.sessionId());
        feedback.setUserMessage(trimTo(request.userMessage(), 4000));
        feedback.setAssistantAnswer(trimTo(request.assistantAnswer(), 8000));
        feedback.setReason(request.reason().strip());
        feedback.setClientIp(clientIp);
        feedback.setStatus("OPEN");
        return toDto(feedbackRepository.save(feedback));
    }

    @Transactional(readOnly = true)
    public Page<ChatFeedbackDto> list(String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            return feedbackRepository
                    .findByStatusOrderByCreatedAtDesc(status.strip().toUpperCase(), pageable)
                    .map(this::toDto);
        }
        return feedbackRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toDto);
    }

    @Transactional
    public ChatFeedbackDto markReviewed(UUID id) {
        ChatFeedback feedback = feedbackRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found: " + id));
        feedback.setStatus("REVIEWED");
        return toDto(feedbackRepository.save(feedback));
    }

    private ChatFeedbackDto toDto(ChatFeedback feedback) {
        return new ChatFeedbackDto(
                feedback.getId(),
                feedback.getUserId(),
                feedback.getSessionId(),
                feedback.getUserMessage(),
                feedback.getAssistantAnswer(),
                feedback.getReason(),
                feedback.getClientIp(),
                feedback.getStatus(),
                feedback.getCreatedAt());
    }

    private static String trimTo(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max);
    }
}

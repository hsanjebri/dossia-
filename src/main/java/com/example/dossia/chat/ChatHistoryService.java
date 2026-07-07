package com.example.dossia.chat;

import com.example.dossia.chat.domain.ChatMessageEntity;
import com.example.dossia.chat.domain.ChatSession;
import com.example.dossia.chat.domain.MessageRole;
import com.example.dossia.chat.dto.ChatSessionDetailDto;
import com.example.dossia.chat.dto.ChatSessionSummaryDto;
import com.example.dossia.chat.dto.ChatSourceDto;
import com.example.dossia.chat.dto.StoredChatMessageDto;
import com.example.dossia.chat.repository.ChatSessionRepository;
import com.example.dossia.common.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatHistoryService {

    private static final int TITLE_MAX = 80;
    private static final int PREVIEW_MAX = 120;

    private final ChatSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    public ChatHistoryService(ChatSessionRepository sessionRepository, ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ChatSessionSummaryDto> listSessions(UUID userId) {
        return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatSessionDetailDto getSession(UUID userId, UUID sessionId) {
        ChatSession session = requireSession(userId, sessionId);
        return new ChatSessionDetailDto(
                session.getId(),
                session.getTitle(),
                session.getUpdatedAt(),
                session.getMessages().stream().map(this::toStoredMessage).toList());
    }

    @Transactional
    public void deleteSession(UUID userId, UUID sessionId) {
        ChatSession session = requireSession(userId, sessionId);
        sessionRepository.delete(session);
    }

    @Transactional(readOnly = true)
    public List<ConversationTurn> getRecentTurns(UUID userId, UUID sessionId, int maxMessages) {
        if (userId == null || sessionId == null || maxMessages <= 0) {
            return List.of();
        }
        ChatSession session = requireSession(userId, sessionId);
        List<ChatMessageEntity> messages = session.getMessages();
        if (messages.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, messages.size() - maxMessages);
        return messages.subList(from, messages.size()).stream()
                .map(message -> new ConversationTurn(
                        message.getRole() == MessageRole.USER ? "user" : "model", message.getContent()))
                .toList();
    }

    @Transactional
    public ChatSession resolveSession(UUID userId, UUID sessionId, String firstMessage) {
        if (sessionId != null) {
            return requireSession(userId, sessionId);
        }
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(buildTitle(firstMessage));
        return sessionRepository.save(session);
    }

    @Transactional
    public void appendUserMessage(ChatSession session, String content) {
        session.getMessages().add(buildMessage(session, MessageRole.USER, content, null));
        sessionRepository.save(session);
    }

    @Transactional
    public void appendAssistantMessage(ChatSession session, String content, List<ChatSourceDto> sources) {
        session.getMessages().add(buildMessage(session, MessageRole.ASSISTANT, content, sources));
        sessionRepository.save(session);
    }

    private ChatSession requireSession(UUID userId, UUID sessionId) {
        return sessionRepository
                .findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));
    }

    private ChatSessionSummaryDto toSummary(ChatSession session) {
        String preview = session.getMessages().isEmpty()
                ? ""
                : truncate(session.getMessages().get(session.getMessages().size() - 1).getContent(), PREVIEW_MAX);
        return new ChatSessionSummaryDto(session.getId(), session.getTitle(), preview, session.getUpdatedAt());
    }

    private StoredChatMessageDto toStoredMessage(ChatMessageEntity message) {
        return new StoredChatMessageDto(
                message.getId(),
                message.getRole().name().toLowerCase(),
                message.getContent(),
                readSources(message.getSourcesJson()),
                message.getCreatedAt());
    }

    private ChatMessageEntity buildMessage(
            ChatSession session, MessageRole role, String content, List<ChatSourceDto> sources) {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setSession(session);
        message.setRole(role);
        message.setContent(content);
        message.setSourcesJson(writeSources(sources));
        return message;
    }

    private String buildTitle(String text) {
        String cleaned = text.strip().replaceAll("\\s+", " ");
        if (cleaned.length() <= TITLE_MAX) {
            return cleaned.isEmpty() ? "Nouvelle conversation" : cleaned;
        }
        return cleaned.substring(0, TITLE_MAX - 1) + "…";
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String cleaned = text.strip().replaceAll("\\s+", " ");
        if (cleaned.length() <= max) {
            return cleaned;
        }
        return cleaned.substring(0, max - 1) + "…";
    }

    private String writeSources(List<ChatSourceDto> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private List<ChatSourceDto> readSources(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }
}

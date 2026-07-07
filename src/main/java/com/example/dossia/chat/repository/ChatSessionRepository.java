package com.example.dossia.chat.repository;

import com.example.dossia.chat.domain.ChatSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    @Query("SELECT s FROM ChatSession s WHERE s.userId = :userId ORDER BY s.updatedAt DESC")
    List<ChatSession> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<ChatSession> findByIdAndUserId(UUID id, UUID userId);
}

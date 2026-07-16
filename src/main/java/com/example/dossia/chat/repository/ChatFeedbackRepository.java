package com.example.dossia.chat.repository;

import com.example.dossia.chat.domain.ChatFeedback;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatFeedbackRepository extends JpaRepository<ChatFeedback, UUID> {

    Page<ChatFeedback> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<ChatFeedback> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

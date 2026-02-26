package com.webdynamo.document_insight.repo;

import com.webdynamo.document_insight.model.Conversation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @EntityGraph(attributePaths = {"messages"})
    List<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"messages"})
    Optional<Conversation> findByIdAndUserId(Long id, Long userId);

    List<Conversation> findByUpdatedAtBefore(LocalDateTime date);
}



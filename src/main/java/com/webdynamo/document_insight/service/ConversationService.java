package com.webdynamo.document_insight.service;

import com.webdynamo.document_insight.dto.RAGResponse;
import com.webdynamo.document_insight.model.Conversation;
import com.webdynamo.document_insight.model.ConversationMessage;
import com.webdynamo.document_insight.model.User;
import com.webdynamo.document_insight.repo.ConversationMessageRepository;
import com.webdynamo.document_insight.repo.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository conversationMessageRepository;

    /**
     * Create new conversation with first message
     */
    public Conversation createConversation(User user, String question, String answer, List<RAGResponse.Source> sources) {
        Conversation conversation = new Conversation();
        conversation.setUser(user);
        conversation.setTitle(generateTitle(question));

        // Save conversation first
        conversation = conversationRepository.save(conversation);

        // Add question message
        ConversationMessage questionMsg = new ConversationMessage();
        questionMsg.setConversation(conversation);
        questionMsg.setType(ConversationMessage.MessageType.QUESTION);
        questionMsg.setContent(question);
        questionMsg = conversationMessageRepository.save(questionMsg);

        // Add answer message
        ConversationMessage answerMsg = new ConversationMessage();
        answerMsg.setConversation(conversation);
        answerMsg.setType(ConversationMessage.MessageType.ANSWER);
        answerMsg.setContent(answer);
        answerMsg.setSources(sources);
        answerMsg = conversationMessageRepository.save(answerMsg);

        // Add messages to conversation so they're available in the returned object
        conversation.getMessages().add(questionMsg);
        conversation.getMessages().add(answerMsg);

        log.info("Created conversation {} for user {}", conversation.getId(), user.getId());
        return conversation;
    }

    /**
     * Add message to existing conversation
     */
    public void addMessage(Long conversationId, User user, String question, String answer, List<RAGResponse.Source> sources) {
        Conversation conversation = conversationRepository
                .findByIdAndUserId(conversationId, user.getId())
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        // Add question
        ConversationMessage questionMsg = new ConversationMessage();
        questionMsg.setConversation(conversation);
        questionMsg.setType(ConversationMessage.MessageType.QUESTION);
        questionMsg.setContent(question);
        conversationMessageRepository.save(questionMsg);

        // Add answer
        ConversationMessage answerMsg = new ConversationMessage();
        answerMsg.setConversation(conversation);
        answerMsg.setType(ConversationMessage.MessageType.ANSWER);
        answerMsg.setContent(answer);
        answerMsg.setSources(sources);
        conversationMessageRepository.save(answerMsg);

        // Update conversation timestamp
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    /**
     * Get all user conversations
     */
    public List<Conversation> getUserConversations(Long userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    /**
     * Get conversation with messages
     */
    public Conversation getConversation(Long conversationId, Long userId) {
        return conversationRepository
                .findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
    }

    /**
     * Delete a conversation (with ownership check)
     */
    public void deleteConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository
                .findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        conversationRepository.delete(conversation);
        log.info("Deleted conversation {} for user {}", conversationId, userId);
    }

    /**
     * Auto-cleanup: delete conversations older than 1 day
     * Runs daily at 2:00 AM
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldConversations() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(1);
        List<Conversation> oldConversations = conversationRepository.findByUpdatedAtBefore(cutoff);

        if (!oldConversations.isEmpty()) {
            conversationRepository.deleteAll(oldConversations);
            log.info("Auto-cleanup: deleted {} conversations older than 30 days", oldConversations.size());
        }
    }

    /**
     * Generate conversation title from first question
     */
    private String generateTitle(String question) {
        // Take first 50 chars or first sentence
        String title = question.length() > 50
                ? question.substring(0, 47) + "..."
                : question;
        return title;
    }
}

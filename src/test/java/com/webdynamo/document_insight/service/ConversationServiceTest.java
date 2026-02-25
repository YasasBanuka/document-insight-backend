package com.webdynamo.document_insight.service;

import com.webdynamo.document_insight.model.Conversation;
import com.webdynamo.document_insight.model.ConversationMessage;
import com.webdynamo.document_insight.model.User;
import com.webdynamo.document_insight.repo.ConversationMessageRepository;
import com.webdynamo.document_insight.repo.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConversationService.
 * Industry Standard: Testing service logic in isolation using Mockito.
 */
@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationMessageRepository messageRepository;

    @InjectMocks
    private ConversationService conversationService;

    private User testUser;
    private Conversation testConversation;

    @BeforeEach
    void setUp() {
        // This runs before every single @Test method.
        // Good for resetting data and ensuring a clean state.
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");

        testConversation = new Conversation();
        testConversation.setId(100L);
        testConversation.setUser(testUser);
        testConversation.setTitle("Test Chat");
        testConversation.setMessages(new ArrayList<>()); // Initialize list to avoid NPE
    }

    @Test
    @DisplayName("Should successfully delete conversation when it exists and belongs to user")
    void deleteConversation_Success() {
        // --- ARRANGE ---
        when(conversationRepository.findByIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(testConversation));

        // --- ACT ---
        conversationService.deleteConversation(100L, 1L);

        // --- ASSERT/VERIFY ---
        verify(conversationRepository, times(1)).delete(testConversation);
    }

    @Test
    @DisplayName("Should throw exception when conversation not found")
    void deleteConversation_NotFound() {
        // --- ARRANGE ---
        when(conversationRepository.findByIdAndUserId(999L, 1L))
                .thenReturn(Optional.empty());

        // --- ACT & ASSERT ---
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            conversationService.deleteConversation(999L, 1L);
        });

        assertEquals("Conversation not found", exception.getMessage());

        // --- VERIFY ---
        verify(conversationRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should create new conversation with question and answer")
    void createConversation_Success() {
        // --- ARRANGE ---
        when(conversationRepository.save(any(Conversation.class))).thenReturn(testConversation);

        // --- ACT ---
        Conversation result = conversationService.createConversation(
                testUser,
                "What is RAG?",
                "RAG is Retrieval Augmented Generation.",
                List.of()
        );

        // --- ASSERT ---
        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals(testUser, result.getUser());

        // --- VERIFY ---
        // 1. Check that conversation was saved once
        verify(conversationRepository, times(1)).save(any(Conversation.class));
        
        // 2. Check that TWO messages were saved (Question + Answer)
        verify(messageRepository, times(2)).save(any(ConversationMessage.class));
    }
}

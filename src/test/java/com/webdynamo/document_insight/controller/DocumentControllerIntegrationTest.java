package com.webdynamo.document_insight.controller;

import com.webdynamo.document_insight.model.Document;
import com.webdynamo.document_insight.model.User;
import com.webdynamo.document_insight.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentController.class)
@AutoConfigureMockMvc
class DocumentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;
    
    @MockitoBean
    private DocumentChunkService documentChunkService;
    
    @MockitoBean
    private VectorSearchService vectorSearchService;
    
    @MockitoBean
    private RAGQueryService ragQueryService;
    
    @MockitoBean
    private ConversationService conversationService;
    
    @MockitoBean
    private JwtService jwtService;
    
    @MockitoBean
    private RateLimitService rateLimitService;
    
    @MockitoBean
    private MetricsService metricsService;
    
    @MockitoBean
    private com.webdynamo.document_insight.repo.UserRepository userRepository;
    
    @MockitoBean
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;
    
    @MockitoBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;
    
    @MockitoBean
    private org.springframework.security.web.AuthenticationEntryPoint authenticationEntryPoint;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // By default, allow all requests through the rate limiter in tests
        when(rateLimitService.tryConsume(any())).thenReturn(true);
    }

    @Test
    @DisplayName("GET /api/documents/{id} - Should return 200 for owner")
    @WithMockUser // Simulates an authenticated user
    void getDocument_AsOwner_ShouldReturnOk() throws Exception {
        // --- ARRANGE ---
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("test@example.com");

        Document doc = new Document();
        doc.setId(100L);
        doc.setUserId(1L); // Same ID as user
        doc.setFilename("test.pdf");

        when(documentService.getDocumentById(100L)).thenReturn(Optional.of(doc));
        when(documentChunkService.getChunkCount(100L)).thenReturn(5L);

        // --- ACT & ASSERT ---
        mockMvc.perform(get("/api/documents/100")
                .with(user(mockUser)) // Inject our mock user into the request
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/documents/{id} - Should return 403 for non-owner")
    @WithMockUser
    void getDocument_AsNonOwner_ShouldReturnForbidden() throws Exception {
        // --- ARRANGE ---
        User userA = new User();
        userA.setId(1L);
        userA.setEmail("userA@example.com");

        Document docOwnedByB = new Document();
        docOwnedByB.setId(100L);
        docOwnedByB.setUserId(2L); // Owner is User 2
        docOwnedByB.setFilename("top-secret.pdf");

        // Mock the service to return the document (even though it belongs to someone else)
        // because the Controller is responsible for the final ownership check.
        when(documentService.getDocumentById(100L)).thenReturn(Optional.of(docOwnedByB));

        // --- ACT & ASSERT ---
        mockMvc.perform(get("/api/documents/100")
                .with(user(userA)) // User 1 making the request
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
    @Test
    @DisplayName("GET /api/documents/conversations/{id} - Should return 400/403 for mismatched user")
    @WithMockUser
    void getConversation_NonOwner_ShouldReturnError() throws Exception {
        // --- ARRANGE ---
        User userA = new User();
        userA.setId(1L);
        userA.setEmail("userA@example.com");
        
        // Mock service to return null for mismatched user (security baseline)
        when(conversationService.getConversation(anyLong(), eq(1L)))
                .thenReturn(null);

        // --- ACT & ASSERT ---
        mockMvc.perform(get("/api/documents/conversations/999")
                .with(user(userA)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GLOBAL Rate Limit - Should return 429 when bucket exhausted")
    @WithMockUser
    void globalRateLimit_ShouldReturn429() throws Exception {
        // --- ARRANGE ---
        // Instead of null, we mock the service to return false on consumption
        when(rateLimitService.tryConsume(any())).thenReturn(false);

        // --- ACT & ASSERT ---
        mockMvc.perform(get("/api/documents")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Rate limit exceeded")));
    }
}

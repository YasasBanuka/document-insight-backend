package com.webdynamo.document_insight.service;

import com.webdynamo.document_insight.dto.RAGResponse;
import com.webdynamo.document_insight.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RAGQueryServiceTest {

    @Mock
    private VectorSearchService vectorSearchService;
    @Mock
    private ChatClient.Builder chatClientBuilder;
    @Mock
    private ChatModel chatModel;
    @Mock
    private MetricsService metricsService;

    @InjectMocks
    private RAGQueryService ragQueryService;

    @Test
    @DisplayName("answerQuestionForUser - Should return answer with sources")
    void answerQuestionForUser_ShouldSucceed() {
        // --- ARRANGE ---
        String question = "What is the policy?";
        Long userId = 1L;
        List<Map<String, Object>> mockChunks = List.of(
                Map.of("content", "Policy details...", "filename", "manual.pdf", "similarity", 0.9, "document_id", 101L)
        );

        when(vectorSearchService.searchSimilarChunksForUser(eq(question), eq(userId), anyInt()))
                .thenReturn(mockChunks);
        when(chatModel.call(anyString())).thenReturn("Processed Policy Answer");

        // --- ACT ---
        RAGResponse result = ragQueryService.answerQuestionForUser(question, userId, 5);

        // --- ASSERT ---
        assertThat(result.getAnswer()).isEqualTo("Processed Policy Answer");
        assertThat(result.getSources()).hasSize(1);
        assertThat(result.getSources().get(0).getFilename()).isEqualTo("manual.pdf");
        
        verify(metricsService).recordRagQuery(1);
    }

    @Test
    @DisplayName("generateAnswerStream - Should stream segments from ChatClient")
    void generateAnswerStream_ShouldReturnFlux() {
        // --- ARRANGE ---
        String query = "Explain chunks";
        Long docId = 101L;
        User user = new User();
        user.setId(1L);

        List<Map<String, Object>> mockChunks = List.of(
                Map.of("content", "Chunk info", "filename", "doc.pdf")
        );

        // Mocking the complex ChatClient builder chain using RETURNS_DEEP_STUBS
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);

        when(vectorSearchService.searchInDocument(eq(docId), eq(query), anyInt())).thenReturn(mockChunks);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        
        // Mocking the fluent chain
        when(chatClient.prompt()
                .user(anyString())
                .stream()
                .content())
                .thenReturn(Flux.just("Segment 1", " Segment 2"));

        // --- ACT ---
        Flux<String> result = ragQueryService.generateAnswerStream(query, docId, user);

        // --- ASSERT ---
        StepVerifier.create(result)
                .expectNext("Segment 1")
                .expectNext(" Segment 2")
                .verifyComplete();

        verify(metricsService).recordRagQuery(1);
    }
}

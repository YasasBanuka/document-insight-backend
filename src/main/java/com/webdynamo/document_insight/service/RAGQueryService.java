package com.webdynamo.document_insight.service;

import com.webdynamo.document_insight.model.DocumentChunk;
import com.webdynamo.document_insight.model.User;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RAGQueryService {

    private final VectorSearchService vectorSearchService;
    private final ChatClient.Builder chatClientBuilder;
    private final ChatModel chatModel;
    private final MetricsService metricsService;

    /**
     * Answer a question using RAG (Retrieval-Augmented Generation)
     */
    public String answerQuestion(String question, int contextChunks) {
        log.info("Answering question: {}", question);

        // Step 1: Retrieve relevant chunks using vector search
        List<Map<String, Object>> searchResults = vectorSearchService.searchSimilarChunks(
                question,
                contextChunks
        );

        if (searchResults.isEmpty()) {
            return "I couldn't find any relevant information to answer your question.";
        }

        // Step 2: Build context from search results
        String context = buildContext(searchResults);
        log.debug("Built context from {} chunks", searchResults.size());

        // Step 3: Create prompt with context and question
        String promptText = buildPrompt(question, context);

        // Step 4: Generate answer using Ollama
        ChatClient chatClient = chatClientBuilder.build();
        String answer = chatClient.prompt()
                .user(promptText)
                .call()
                .content();

        log.info("Generated answer of length: {}", answer.length());
        return answer;
    }

    /**
     * Answer question within a specific document
     */
    public String answerQuestionInDocument(Long documentId, String question, int contextChunks) {
        log.info("Answering question in document {}: {}", documentId, question);

        // Search only in specific document
        List<Map<String, Object>> searchResults = vectorSearchService.searchInDocument(
                documentId,
                question,
                contextChunks
        );

        if (searchResults.isEmpty()) {
            return "I couldn't find any relevant information in this document to answer your question.";
        }

        String context = buildContext(searchResults);
        String promptText = buildPrompt(question, context);

        ChatClient chatClient = chatClientBuilder.build();
        String answer = chatClient.prompt()
                .user(promptText)
                .call()
                .content();

        return answer;
    }

    /**
     * Build context string from search results
     */
    private String buildContext(List<Map<String, Object>> searchResults) {
        StringBuilder context = new StringBuilder();

        for (int i = 0; i < searchResults.size(); i++) {
            Map<String, Object> result = searchResults.get(i);
            String content = (String) result.get("content");
            String filename = (String) result.get("filename");

            context.append(String.format("Document: %s\n", filename));
            context.append(String.format("Content: %s\n\n", content));
        }

        return context.toString();
    }

    /**
     * Build prompt template for RAG
     */
    private String buildPrompt(String question, String context) {
        String template = """
            You are a helpful AI assistant that answers questions based on the provided context.
            Use ONLY the information from the context below to answer the question.
            If the context doesn't contain enough information to answer, say so honestly.
            Be concise but thorough. Use a professional, friendly tone.
            
            CONTEXT:
            {context}
            
            QUESTION:
            {question}
            
            ANSWER:
            """;

        PromptTemplate promptTemplate = new PromptTemplate(template);
        Prompt prompt = promptTemplate.create(Map.of(
                "context", context,
                "question", question
        ));

        return prompt.getContents();
    }

    /**
     * Answer question using only user's documents
     */
    public String answerQuestionForUser(String question, Long userId, int contextChunks) {
        log.info("RAG Query for user {}: {}", userId, question);

        // Search only user's documents
        List<Map<String, Object>> relevantChunks = vectorSearchService
                .searchSimilarChunksForUser(question, userId, contextChunks);

        if (relevantChunks.isEmpty()) {
            return "I don't have enough information in your documents to answer this question.";
        }

        // Build context from chunks
        String context = relevantChunks.stream()
                .map(chunk -> (String) chunk.get("content"))
                .collect(Collectors.joining("\n\n"));

        // Build prompt
        String prompt = String.format("""
            You are a helpful AI assistant. Answer the question based ONLY on the provided context.
            
            Context:
            %s
            
            Question: %s
            
            Answer:
            """, context, question);

        // Generate answer
        return chatModel.call(prompt);
    }

    public Flux<String> generateAnswerStream(String query, Long documentId, User user) {
        log.info("Streaming RAG answer for user {} on document {}", user.getId(), documentId);

        // Step 1: Search for relevant chunks in the document
        List<Map<String, Object>> searchResults = vectorSearchService.searchInDocument(
                documentId,
                query,
                7  // Get 7 context chunks
        );

        if (searchResults.isEmpty()) {
            return Flux.just("I couldn't find relevant information in this document.");
        }

        // Step 2: Track metrics
        metricsService.recordRagQuery(searchResults.size());

        // Step 3: Build context
        String context = buildContext(searchResults);

        // Step 4: Build prompt
        String promptText = buildPrompt(query, context);

        // Step 5: Stream response from Ollama
        ChatClient chatClient = chatClientBuilder.build();

        return chatClient.prompt()
                .user(promptText)
                .stream()
                .content();  // Returns Flux<String>
    }
}

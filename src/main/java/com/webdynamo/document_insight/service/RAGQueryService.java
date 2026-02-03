package com.webdynamo.document_insight.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RAGQueryService {

    private final VectorSearchService vectorSearchService;
    private final ChatClient.Builder chatClientBuilder;

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
}

package com.webdynamo.document_insight.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    /**
     * Generate embedding for a single text
     */
    public float[] generateEmbedding(String text) {
        log.debug("Generating embedding for text of length: {}", text.length());

        try {
            // Create embedding request
            EmbeddingRequest request = new EmbeddingRequest(List.of(text), null);

            // Get embedding response
            EmbeddingResponse response = embeddingModel.call(request);

            // Extract the embedding (float array)
            float[] embedding = response.getResults().get(0).getOutput();

            log.debug("Generated embedding with dimension: {}", embedding.length);
            return embedding;

        } catch (Exception e) {
            log.error("Error generating embedding for text", e);
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }

    /**
     * Generate embeddings for multiple texts (batch processing)
     */
    public List<float[]> generateEmbeddings(List<String> texts) {
        log.info("Generating embeddings for {} texts", texts.size());

        try {
            // Create batch request
            EmbeddingRequest request = new EmbeddingRequest(texts, null);

            // Get embeddings
            EmbeddingResponse response = embeddingModel.call(request);

            // Extract all embeddings
            List<float[]> embeddings = response.getResults().stream()
                    .map(result -> result.getOutput())
                    .collect(Collectors.toList());

            log.info("Generated {} embeddings", embeddings.size());
            return embeddings;

        } catch (Exception e) {
            log.error("Error generating batch embeddings", e);
            throw new RuntimeException("Failed to generate embeddings", e);
        }
    }

    /**
     * Convert float[] embedding to PostgreSQL vector format string
     */
    public String embeddingToVector(float[] embedding) {
        // Format: [0.1,0.2,0.3,...] (PostgreSQL vector format)
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            sb.append(embedding[i]);
            if (i < embedding.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Convert float[] to List<Float> if needed
     */
    public List<Float> embeddingToList(float[] embedding) {
        List<Float> list = new java.util.ArrayList<>();
        for (float value : embedding) {
            list.add(value);
        }
        return list;
    }
}

package com.webdynamo.document_insight.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class TextChunkingService {

    // Chunk size in characters (roughly 500 tokens)
    private static final int CHUNK_SIZE = 2000;

    // Overlap between chunks (prevents context loss)
    private static final int CHUNK_OVERLAP = 200;

    /**
     * Split text into chunks with overlap
     */
    public List<String> chunkText(String text) {
        log.debug("Chunking text of length: {}", text.length());

        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            // Calculate end position
            int end = Math.min(start + CHUNK_SIZE, text.length());

            // Try to break at sentence boundary (period followed by space)
            if (end < text.length()) {
                int lastPeriod = text.lastIndexOf(". ", end);
                if (lastPeriod > start + CHUNK_SIZE / 2) {
                    // Found a good break point
                    end = lastPeriod + 1;
                }
            }

            // Extract chunk
            String chunk = text.substring(start, end).trim();

            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            // Move to next chunk with overlap
            start = end - CHUNK_OVERLAP;

            // Prevent infinite loop if chunk is too small
            if (start + CHUNK_SIZE >= text.length() && start < text.length()) {
                start = text.length();
            }
        }

        log.info("Text chunked into {} chunks", chunks.size());
        return chunks;
    }

    /**
     * Estimate token count (rough approximation: 1 token ≈ 4 characters)
     */
    public int estimateTokenCount(String text) {
        return text.length() / 4;
    }
}

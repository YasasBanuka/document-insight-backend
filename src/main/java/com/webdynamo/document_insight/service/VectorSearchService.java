package com.webdynamo.document_insight.service;

import com.webdynamo.document_insight.repo.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class VectorSearchService {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;
    private final DocumentChunkRepository documentChunkRepository;

    /**
     * Search for similar chunks using vector similarity
     */
    public List<Map<String, Object>> searchSimilarChunks(String query, int limit) {
        log.info("Searching for similar chunks to: {}", query);

        // 1. Generate embedding for the query
        float[] queryEmbedding = embeddingService.generateEmbedding(query);
        String queryVector = embeddingService.embeddingToVector(queryEmbedding);

        log.debug("Query embedding dimension: {}", queryEmbedding.length);

        // 2. Use PostgreSQL to find similar vectors
        // Cast TEXT to vector and use cosine distance operator
        String sql = """
            SELECT 
                dc.id,
                dc.chunk_index,
                dc.content,
                dc.token_count,
                d.filename,
                d.id as document_id,
                1 - (dc.embedding::vector <=> ?::vector) as similarity
            FROM document_chunks dc
            JOIN documents d ON dc.document_id = d.id
            WHERE dc.embedding IS NOT NULL
            ORDER BY dc.embedding::vector <=> ?::vector
            LIMIT ?
            """;

        // Execute query
        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                sql,
                queryVector,  // First placeholder
                queryVector,  // Second placeholder (for ORDER BY)
                limit
        );

        log.info("Found {} similar chunks", results.size());
        return results;
    }

    /**
     * Search within a specific document
     */
    public List<Map<String, Object>> searchInDocument(Long documentId, String query, int limit) {
        log.info("Searching in document {} for: {}", documentId, query);

        // Generate query embedding
        float[] queryEmbedding = embeddingService.generateEmbedding(query);
        String queryVector = embeddingService.embeddingToVector(queryEmbedding);

        // Search only in specific document
        String sql = """
            SELECT 
                dc.id,
                dc.chunk_index,
                dc.content,
                dc.token_count,
                d.filename,
                1 - (dc.embedding::vector <=> ?::vector) as similarity
            FROM document_chunks dc
            JOIN documents d ON dc.document_id = d.id
            WHERE dc.document_id = ?
              AND dc.embedding IS NOT NULL
            ORDER BY dc.embedding::vector <=> ?::vector
            LIMIT ?
            """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                sql,
                queryVector,
                documentId,
                queryVector,
                limit
        );

        log.info("Found {} similar chunks in document", results.size());
        return results;
    }
}

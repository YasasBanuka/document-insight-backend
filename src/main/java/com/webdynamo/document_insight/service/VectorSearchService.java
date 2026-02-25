package com.webdynamo.document_insight.service;

import com.webdynamo.document_insight.repo.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
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

    /**
     * Search with pagination
     */
    public Map<String, Object> searchSimilarChunksWithPagination(
            String query,
            int page,
            int size) {

        log.info("Paginated search: query='{}', page={}, size={}", query, page, size);

        // Generate query embedding
        float[] queryEmbedding = embeddingService.generateEmbedding(query);
        String queryVector = embeddingService.embeddingToVector(queryEmbedding);

        // Calculate offset
        int offset = page * size;

        // Get total count
        String countSql = """
        SELECT COUNT(*) 
        FROM document_chunks 
        WHERE embedding IS NOT NULL
        """;

        Integer totalElements = jdbcTemplate.queryForObject(countSql, Integer.class);

        // Get paginated results
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
        LIMIT ? OFFSET ?
        """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                sql,
                queryVector,
                queryVector,
                size,
                offset
        );

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("content", results);
        response.put("page", page);
        response.put("size", size);
        response.put("totalElements", totalElements);
        response.put("totalPages", (int) Math.ceil((double) totalElements / size));

        log.info("Found {} results, page {} of {}",
                results.size(), page, response.get("totalPages"));

        return response;
    }

    /**
     * Search similar chunks only within user's documents
     */
    public List<Map<String, Object>> searchSimilarChunksForUser(
            String query,
            Long userId,
            int limit
    ) {
        log.info("Searching similar chunks for user: {} with query: {}", userId, query);

        // Generate query embedding
        float[] queryEmbedding = embeddingService.generateEmbedding(query);
        String queryVector = embeddingService.embeddingToVector(queryEmbedding);

        // SQL query with userId filter (positional parameters)
        String sql = """
        SELECT 
            dc.id,
            dc.document_id,
            dc.chunk_index,
            dc.content,
            dc.token_count,
            d.filename,
            1 - (dc.embedding::vector <=> ?::vector) as similarity
        FROM document_chunks dc
        JOIN documents d ON dc.document_id = d.id
        WHERE d.user_id = ?
          AND dc.embedding IS NOT NULL
        ORDER BY dc.embedding::vector <=> ?::vector
        LIMIT ?
        """;

        // Execute with positional parameters
        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                sql,
                queryVector,   // First ?
                userId,        // Second ?
                queryVector,   // Third ? (for ORDER BY)
                limit          // Fourth ?
        );

        log.info("Found {} results for user: {}", results.size(), userId);
        return results;
    }

    /**
     * Search similar chunks for user with pagination
     */
    public Map<String, Object> searchSimilarChunksForUserPaginated(
            String query,
            Long userId,
            int page,
            int size
    ) {
        log.info("Paginated vector search for user {}: '{}' (page: {}, size: {})",
                userId, query, page, size);

        try {
            // Generate embedding for query
            float[] queryEmbedding = embeddingService.generateEmbedding(query);

            // Convert to pgvector format using existing method
            String queryVector = embeddingService.embeddingToVector(queryEmbedding);

            // Calculate pagination
            int offset = page * size;

            // Search with user filtering
            String sql = """
            SELECT 
                dc.id,
                dc.document_id,
                dc.chunk_index,
                dc.content,
                dc.token_count,
                d.filename,
                d.user_id,
                1 - (dc.embedding::vector <=> ?::vector) as similarity
            FROM document_chunks dc
            JOIN documents d ON dc.document_id = d.id
            WHERE d.user_id = ?
              AND dc.embedding IS NOT NULL
            ORDER BY dc.embedding::vector <=> ?::vector
            LIMIT ? OFFSET ?
            """;

            // Execute query (simpler than custom RowMapper)
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                    sql,
                    queryVector,  // First ?
                    userId,       // Second ?
                    queryVector,  // Third ? (ORDER BY)
                    size,         // Fourth ?
                    offset        // Fifth ?
            );

            // Get total count for user
            String countSql = """
            SELECT COUNT(*)
            FROM document_chunks dc
            JOIN documents d ON dc.document_id = d.id
            WHERE d.user_id = ?
              AND dc.embedding IS NOT NULL
            """;

            Integer totalElements = jdbcTemplate.queryForObject(countSql, Integer.class, userId);

            // Build paginated response
            Map<String, Object> response = new HashMap<>();
            response.put("content", results);
            response.put("currentPage", page);
            response.put("pageSize", size);
            response.put("totalElements", totalElements);
            response.put("totalPages", (int) Math.ceil((double) totalElements / size));

            log.info("Found {} results for user {}, page {} of {}",
                    results.size(), userId, page, response.get("totalPages"));

            return response;

        } catch (Exception e) {
            log.error("Paginated search failed for user {}", userId, e);
            throw new RuntimeException("Paginated search failed", e);
        }
    }
}
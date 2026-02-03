package com.webdynamo.document_insight.controller;

import com.webdynamo.document_insight.dto.DocumentDTO;
import com.webdynamo.document_insight.dto.QuestionRequest;
import com.webdynamo.document_insight.dto.UploadResponse;
import com.webdynamo.document_insight.model.Document;
import com.webdynamo.document_insight.model.DocumentChunk;
import com.webdynamo.document_insight.service.DocumentChunkService;
import com.webdynamo.document_insight.service.DocumentService;
import com.webdynamo.document_insight.service.RAGQueryService;
import com.webdynamo.document_insight.service.VectorSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Documents", description = "Document management and RAG query endpoints")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentChunkService documentChunkService;
    private final VectorSearchService vectorSearchService;
    private final RAGQueryService ragQueryService;

    /**
     * Upload a new document with full processing
     */
    @Operation(
            summary = "Upload a document",
            description = "Upload PDF, DOCX, or TXT file for processing and embedding generation"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Document uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file or file too large"),
            @ApiResponse(responseCode = "500", description = "Server error")
    })
    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> uploadDocument (
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", defaultValue = "1") Long userId) {

        log.info("Upload request received: {} ({})", file.getOriginalFilename(), file.getContentType());

        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        new UploadResponse(null, null, "File is empty", 0L, null)
                );
            }

            // Upload and process (parse + chunk)
            Document document = documentService.uploadAndProcessDocument(file, userId);

            // Get chunk count
            Long chunkCount = documentChunkService.getChunkCount(document.getId());

            UploadResponse response = new UploadResponse(
                    document.getId(),
                    document.getFilename(),
                    "File uploaded and processed successfully. " + chunkCount + " chunks created.",
                    document.getFileSize(),
                    document.getContentType()
            );

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("Upload failed", e);
            return ResponseEntity.badRequest().body(
                    new UploadResponse(null, file.getOriginalFilename(), "Upload failed: " + e.getMessage(), 0L, null)
            );
        }
    }

    /**
     * Ask a question across all documents (RAG)
     */
    @Operation(
            summary = "Ask a question (RAG)",
            description = "Ask a question and get AI-generated answer based on document knowledge"
    )
    @GetMapping("/ask")
    public ResponseEntity<Map<String, Object>> askQuestion(
            @Valid @ModelAttribute QuestionRequest request) {  // ← Just this!
        log.info("RAG Query: {}", request.getQuestion());
        try {
            String answer = ragQueryService.answerQuestion(
                    request.getQuestion(),
                    request.getContextChunks()
            );
            Map<String, Object> response = new HashMap<>();
            response.put("question", request.getQuestion());
            response.put("answer", answer);
            response.put("contextChunks", request.getContextChunks());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RAG query failed", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all documents for a user
     */
    @GetMapping
    public ResponseEntity<List<DocumentDTO>> getUserDocuments(
            @RequestParam(defaultValue = "1") Long userId) {

        log.info("Fetching documents for user: {}", userId);

        List<Document> documents = documentService.getUserDocuments(userId);

        // Convert entities to DTOs
        List<DocumentDTO> documentDTOs = documents.stream()
                .map(doc -> {
                    Long chunkCount = documentChunkService.getChunkCount(doc.getId());
                    return new DocumentDTO(doc, chunkCount);
                })
                .collect(Collectors.toList());

        log.info("Found {} documents for user: {}", documentDTOs.size(), userId);
        return ResponseEntity.ok(documentDTOs);
    }

    /**
     * Get a specific document by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<DocumentDTO> getDocument(@PathVariable Long id) {
        log.info("Fetching document: {}", id);

        return documentService.getDocumentById(id)
                .map(doc -> {
                    Long chunkCount = documentChunkService.getChunkCount(doc.getId());
                    return ResponseEntity.ok(new DocumentDTO(doc, chunkCount));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete a document
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long userId) {

        log.info("Deleting document: {}", id);

        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get document statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Object> getStats(@RequestParam(defaultValue = "1") Long userId) {
        log.info("Fetching stats for user: {}", userId);

        long totalDocuments = documentService.getUserDocumentCount(userId);

        return ResponseEntity.ok(
                new java.util.HashMap<String, Object>() {{
                    put("userId", userId);
                    put("totalDocuments", totalDocuments);
                }}
        );
    }



    /**
     * Get all chunks for a document
     */
    @GetMapping("/{id}/chunks")
    public ResponseEntity<List<Map<String, Object>>> getDocumentChunks(@PathVariable Long id) {
        log.info("Fetching chunks for document: {}", id);

        List<DocumentChunk> chunks = documentChunkService.getChunksForDocument(id);

        // Convert to simple DTO
        List<Map<String, Object>> chunkDTOs = chunks.stream()
                .map(chunk -> {
                    Map<String, Object> dto = new HashMap<>();
                    dto.put("chunkIndex", chunk.getChunkIndex());
                    dto.put("content", chunk.getContent());
                    dto.put("tokenCount", chunk.getTokenCount());
                    return dto;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(chunkDTOs);
    }

    /**
     * Search across all documents
     */
    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchDocuments(
            @RequestParam("query") String query,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {

        log.info("Search request: {} (limit: {})", query, limit);

        try {
            List<Map<String, Object>> results = vectorSearchService.searchSimilarChunks(query, limit);
            return ResponseEntity.ok(results);

        } catch (Exception e) {
            log.error("Search failed", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search within a specific document
     */
    @GetMapping("/{id}/search")
    public ResponseEntity<List<Map<String, Object>>> searchInDocument(
            @PathVariable Long id,
            @RequestParam("query") String query,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {

        log.info("Search in document {} for: {}", id, query);

        try {
            List<Map<String, Object>> results = vectorSearchService.searchInDocument(id, query, limit);
            return ResponseEntity.ok(results);

        } catch (Exception e) {
            log.error("Search failed", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Ask a question about a specific document (RAG)
     */
    @GetMapping("/{id}/ask")
    public ResponseEntity<Map<String, Object>> askQuestionInDocument(
            @PathVariable Long id,
            @RequestParam("question") String question,
            @RequestParam(value = "contextChunks", defaultValue = "3") int contextChunks) {

        log.info("RAG Query in document {}: {}", id, question);

        try {
            String answer = ragQueryService.answerQuestionInDocument(id, question, contextChunks);

            Map<String, Object> response = new HashMap<>();
            response.put("documentId", id);
            response.put("question", question);
            response.put("answer", answer);
            response.put("contextChunks", contextChunks);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("RAG query failed", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Paginated search
     */
    @GetMapping("/search/paginated")
    public ResponseEntity<Map<String, Object>> searchDocumentsPaginated(
            @RequestParam("query") String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "5") int size) {

        log.info("Paginated search: query='{}', page={}, size={}", query, page, size);

        try {
            Map<String, Object> results = vectorSearchService.searchSimilarChunksWithPagination(
                    query, page, size
            );
            return ResponseEntity.ok(results);

        } catch (Exception e) {
            log.error("Paginated search failed", e);
            return ResponseEntity.badRequest().build();
        }
    }


}

package com.webdynamo.document_insight.controller;

import com.webdynamo.document_insight.dto.DocumentDTO;
import com.webdynamo.document_insight.dto.UploadResponse;
import com.webdynamo.document_insight.model.Document;
import com.webdynamo.document_insight.model.DocumentChunk;
import com.webdynamo.document_insight.service.DocumentChunkService;
import com.webdynamo.document_insight.service.DocumentService;
import com.webdynamo.document_insight.service.VectorSearchService;
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
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentChunkService documentChunkService;
    private final VectorSearchService vectorSearchService;

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
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        log.info("Deleting document: {}", id);

        try {
            documentService.deleteDocument(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            log.error("Error deleting document: {}", id, e);
            return ResponseEntity.notFound().build();
        }
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
     * Upload a new document with full processing
     */
    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> uploadDocument(
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
}

package com.webdynamo.document_insight.controller;

import com.webdynamo.document_insight.dto.DocumentDTO;
import com.webdynamo.document_insight.dto.UploadResponse;
import com.webdynamo.document_insight.model.Document;
import com.webdynamo.document_insight.service.DocumentChunkService;
import com.webdynamo.document_insight.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentChunkService documentChunkService;

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
     * Upload a new document
     */
    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", defaultValue = "1") Long userId) {

        log.info("Upload request received: {} ({})", file.getOriginalFilename(), file.getContentType());

        try {
            // Validate file is not empty
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        new UploadResponse(null, null, "File is empty", 0L, null)
                );
            }

            // Upload and save document
            Document document = documentService.uploadDocument(file, userId);

            // Create response
            UploadResponse response = new UploadResponse(
                    document.getId(),
                    document.getFilename(),
                    "File uploaded successfully",
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
}

package com.webdynamo.document_insight.controller;

import com.webdynamo.document_insight.model.Document;
import com.webdynamo.document_insight.service.DocumentParserService;
import com.webdynamo.document_insight.service.DocumentService;
import com.webdynamo.document_insight.service.TextChunkingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Slf4j
public class TestController {

    private final DocumentService documentService;

    @GetMapping("/hello")
    public ResponseEntity<String> sayHello() {
        log.info("Hello endpoint called");
        return ResponseEntity.ok("Hello from Document Insight Engine!");
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        log.info("Status endpoint called");

        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("timestamp", LocalDateTime.now());
        status.put("service", "document-insight");

        return ResponseEntity.ok(status);
    }

    @GetMapping("/echo/{message}")
    public ResponseEntity<String> echo(@PathVariable String message) {
        log.info("Echo endpoint called with message: {}", message);
        return ResponseEntity.ok("You said: " + message);
    }

    @PostMapping("/init-sample-data")
    public ResponseEntity<String> initializeSampleData() {
        log.info("Initializing sample data");

        try {
            // Create sample documents
            Document doc1 = new Document();
            doc1.setFilename("sample-resume.pdf");
            doc1.setContentType("application/pdf");
            doc1.setFilePath("/uploads/sample-resume.pdf");
            doc1.setFileSize(1024000L);
            doc1.setUserId(1L);

            Document doc2 = new Document();
            doc2.setFilename("research-paper.docx");
            doc2.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            doc2.setFilePath("/uploads/research-paper.docx");
            doc2.setFileSize(2048000L);
            doc2.setUserId(1L);

            Document doc3 = new Document();
            doc3.setFilename("meeting-notes.txt");
            doc3.setContentType("text/plain");
            doc3.setFilePath("/uploads/meeting-notes.txt");
            doc3.setFileSize(5120L);
            doc3.setUserId(2L);

            // Save documents
            documentService.saveDocument(doc1);
            documentService.saveDocument(doc2);
            documentService.saveDocument(doc3);

            log.info("Sample data created successfully");
            return ResponseEntity.ok("Sample data initialized: 3 documents created");

        } catch (Exception e) {
            log.error("Error initializing sample data", e);
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/test-parse")
    public ResponseEntity<String> testParse(@RequestParam("file") MultipartFile file) {
        try {
            // Save file temporarily
            Path tempFile = Files.createTempFile("test", file.getOriginalFilename());
            file.transferTo(tempFile.toFile());

            // Parse it
            DocumentParserService parser = new DocumentParserService();
            String text = parser.parseDocument(tempFile, file.getContentType());

            // Clean up
            Files.delete(tempFile);

            return ResponseEntity.ok("Extracted text:\n\n" + text);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/test-chunk")
    public ResponseEntity<Object> testChunk(@RequestParam("text") String text) {
        TextChunkingService chunker = new TextChunkingService();
        List<String> chunks = chunker.chunkText(text);

        Map<String, Object> result = new HashMap<>();
        result.put("originalLength", text.length());
        result.put("chunkCount", chunks.size());
        result.put("chunks", chunks);

        return ResponseEntity.ok(result);
    }
}

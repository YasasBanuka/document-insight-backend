package com.webdynamo.document_insight.service;

import com.webdynamo.document_insight.exception.DocumentNotFoundException;
import com.webdynamo.document_insight.model.Document;
import com.webdynamo.document_insight.model.DocumentChunk;
import com.webdynamo.document_insight.repo.DocumentChunkRepository;
import com.webdynamo.document_insight.repo.DocumentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkService documentChunkService;
    private final FileStorageService fileStorageService;
    private final DocumentParserService documentParserService;
    private final TextChunkingService textChunkingService;
    private final DocumentChunkRepository documentChunkRepository;
    private final EmbeddingService embeddingService;

    /**
     * Get all documents for a specific user
     */
    public List<Document> getUserDocuments(Long userId) {
        log.debug("Fetching documents for user: {}", userId);
        return documentRepository.findByUserId(userId);
    }

    /**
     * Get a specific document by ID
     */
    public Optional<Document> getDocumentById(Long id) {
        log.debug("Fetching document with id: {}", id);
        return documentRepository.findById(id);
    }

    /**
     * Delete a document and all its chunks
     */
    @Transactional
    public void deleteDocument(Long id) {
        log.info("Deleting document with id: {}", id);

        // Check if document exists
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));

        // Delete all chunks first
        documentChunkService.deleteAllChunksForDocument(id);

        // Delete physical file
        if (document.getFilePath() != null) {
            fileStorageService.deleteFile(document.getFilePath());
        }

        // Delete document from database
        documentRepository.delete(document);
        log.info("Document deleted successfully: {}", id);
    }

    /**
     * Get total document count for a user
     */
    public long getUserDocumentCount(Long userId) {
        return documentRepository.findByUserId(userId).size();
    }

    /**
     * Save a new document
     */
    @Transactional
    public Document saveDocument(Document document) {
        log.info("Saving new document: {}", document.getFilename());
        Document saved = documentRepository.save(document);
        log.info("Document saved with id: {}", saved.getId());
        return saved;
    }

    /**
     * Upload and save a new document
     */
    @Transactional
    public Document uploadDocument(MultipartFile file, Long userId) {
        log.info("Uploading document: {} for user: {}", file.getOriginalFilename(), userId);

        // Validate file type
        if (!fileStorageService.isValidFileType(file.getContentType())) {
            throw new RuntimeException("Unsupported file type: " + file.getContentType());
        }

        // Validate file size (additional check beyond Spring's limit)
        if (file.getSize() > 10 * 1024 * 1024) { // 10MB
            throw new RuntimeException("File size exceeds maximum limit of 10MB");
        }

        // Store file
        String storedFilename = fileStorageService.storeFile(file);

        // Create document entity
        Document document = new Document();
        document.setFilename(file.getOriginalFilename());
        document.setContentType(file.getContentType());
        document.setFilePath(storedFilename);  // Store the unique filename
        document.setFileSize(file.getSize());
        document.setUserId(userId);

        // Save to database
        Document saved = documentRepository.save(document);

        log.info("Document uploaded successfully with id: {}", saved.getId());
        return saved;
    }

    /**
     * Upload document with full processing: parse and chunk
     */
    @Transactional
    public Document uploadAndProcessDocument(MultipartFile file, Long userId) {
        log.info("Uploading and processing document: {} for user: {}", file.getOriginalFilename(), userId);

        // Validate file type
        if (!fileStorageService.isValidFileType(file.getContentType())) {
            throw new RuntimeException("Unsupported file type: " + file.getContentType());
        }

        // Store file
        String storedFilename = fileStorageService.storeFile(file);
        Path filePath = fileStorageService.getFilePath(storedFilename);

        // Create document entity
        Document document = new Document();
        document.setFilename(file.getOriginalFilename());
        document.setContentType(file.getContentType());
        document.setFilePath(storedFilename);
        document.setFileSize(file.getSize());
        document.setUserId(userId);

        // Save document first to get ID
        Document savedDocument = documentRepository.save(document);
        log.info("Document saved with id: {}", savedDocument.getId());

        // Parse document to extract text
        String text = documentParserService.parseDocument(filePath, file.getContentType());
        log.info("Extracted {} characters from document", text.length());

        // Chunk the text
        List<String> chunks = textChunkingService.chunkText(text);

        // Generate embeddings and save chunks
        log.info("Generating embeddings for {} chunks", chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String chunkContent = chunks.get(i);

            // Generate embedding for this chunk
            float[] embedding = embeddingService.generateEmbedding(chunkContent);
            String vectorString = embeddingService.embeddingToVector(embedding);

            // Create and save chunk with embedding
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocument(savedDocument);
            chunk.setChunkIndex(i);
            chunk.setContent(chunkContent);
            chunk.setTokenCount(textChunkingService.estimateTokenCount(chunkContent));
            chunk.setEmbedding(vectorString);  // ✅ Now storing the embedding!

            documentChunkRepository.save(chunk);

            log.debug("Saved chunk {} with embedding dimension: {}", i, embedding.length);
        }
        log.info("All chunks embedded and saved successfully");
        return savedDocument;
    }
}

package com.webdynamo.document_insight.service;

import com.webdynamo.document_insight.model.DocumentChunk;
import com.webdynamo.document_insight.repo.DocumentChunkRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentChunkService {

    private final DocumentChunkRepository documentChunkRepository;


    /**
     * Get all chunks for a document, ordered by their position
     *
     * @param documentId The document ID
     * @return List of chunks in order
     */
    public List<DocumentChunk> getChunksForDocument(Long documentId) {
        log.debug("Fetching chunks for document: {}", documentId);
        List<DocumentChunk> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        log.debug("Found {} chunks for document: {}", chunks.size(), documentId);
        return chunks;
    }

    /**
     * Count total chunks for a document
     *
     * @param documentId The document ID
     * @return Number of chunks
     */
    public Long getChunkCount(Long documentId) {
        log.debug("Counting chunks for document: {}", documentId);
        Long count = documentChunkRepository.countByDocumentId(documentId);
        log.debug("Document {} has {} chunks", documentId, count);
        return count;    }

    /**
     * Delete all chunks associated with a document
     * This is typically called when deleting a document
     *
     * @param documentId The document ID
     */
    @Transactional
    public void deleteAllChunksForDocument(Long documentId) {
        log.info("Deleting all chunks for document: {}", documentId);
        Long count = documentChunkRepository.countByDocumentId(documentId);
        documentChunkRepository.deleteByDocumentId(documentId);
        log.info("Deleted {} chunks for document: {}", count, documentId);
    }

    /**
     * Get chunks that have embeddings (ready for search)
     *
     * @param documentId The document ID
     * @return List of chunks with embeddings
     */
    public List<DocumentChunk> getChunksWithEmbeddings(Long documentId) {
        log.debug("Fetching chunks with embeddings for document: {}", documentId);
        return documentChunkRepository.findChunksWithEmbeddings(documentId);
    }
}

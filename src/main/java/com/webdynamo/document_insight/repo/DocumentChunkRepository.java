package com.webdynamo.document_insight.repo;

import com.webdynamo.document_insight.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    // Find all chunks for a specific document, ordered by position
    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(Long documentId);

    // Count how many chunks a document has
    Long countByDocumentId(Long documentId);

    // Delete all chunks when a document is deleted
    void deleteByDocumentId(Long documentId);

    // Find chunks by document ID (simple version)
    List<DocumentChunk> findByDocumentId(Long documentId);

    // Custom query: Find chunks with embeddings (for vector search)
    @Query("SELECT c FROM DocumentChunk c WHERE c.document.id = :documentId AND c.embedding IS NOT NULL")
    List<DocumentChunk> findChunksWithEmbeddings(@Param("documentId") Long documentId);

}

package com.webdynamo.document_insight.service;

import com.webdynamo.document_insight.model.Document;
import com.webdynamo.document_insight.model.DocumentChunk;
import com.webdynamo.document_insight.repo.DocumentChunkRepository;
import com.webdynamo.document_insight.repo.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentChunkService documentChunkService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private DocumentParserService documentParserService;
    @Mock
    private TextChunkingService textChunkingService;
    @Mock
    private DocumentChunkRepository documentChunkRepository;
    @Mock
    private EmbeddingService embeddingService;

    @InjectMocks
    private DocumentService documentService;

    private MockMultipartFile mockFile;
    private final Long userId = 1L;

    @BeforeEach
    void setUp() {
        mockFile = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "Hello PDF content".getBytes()
        );
    }

    @Test
    @DisplayName("uploadAndProcessDocument - Should complete full pipeline successfully")
    void uploadAndProcessDocument_ShouldSucceed() {
        // --- ARRANGE ---
        String storedFilename = "uuid-test.pdf";
        Path mockPath = Path.of("uploads/" + storedFilename);
        String mockText = "Extracted text content from PDF";
        List<String> mockChunks = List.of("Chunk 1", "Chunk 2");
        float[] mockEmbedding = new float[]{0.1f, 0.2f};
        String mockVector = "[0.1, 0.2]";

        Document savedDoc = new Document();
        savedDoc.setId(10L);
        savedDoc.setFilename("test.pdf");

        when(fileStorageService.isValidFileType(anyString())).thenReturn(true);
        when(fileStorageService.storeFile(any())).thenReturn(storedFilename);
        when(fileStorageService.getFilePath(storedFilename)).thenReturn(mockPath);
        when(documentRepository.save(any(Document.class))).thenReturn(savedDoc);
        when(documentParserService.parseDocument(eq(mockPath), eq("application/pdf"))).thenReturn(mockText);
        when(textChunkingService.chunkText(mockText)).thenReturn(mockChunks);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(mockEmbedding);
        when(embeddingService.embeddingToVector(mockEmbedding)).thenReturn(mockVector);

        // --- ACT ---
        Document result = documentService.uploadAndProcessDocument(mockFile, userId);

        // --- ASSERT ---
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);

        // Verify the orchestration steps
        verify(fileStorageService).storeFile(mockFile);
        verify(documentRepository).save(any(Document.class));
        verify(documentParserService).parseDocument(mockPath, "application/pdf");
        verify(textChunkingService).chunkText(mockText);
        
        // Verify processing for each chunk
        verify(embeddingService, times(2)).generateEmbedding(anyString());
        verify(documentChunkRepository, times(2)).save(any(DocumentChunk.class));
    }

    @Test
    @DisplayName("deleteDocument - Should cleanup all associated resources")
    void deleteDocument_ShouldCleanupSuccessfully() {
        // --- ARRANGE ---
        Long docId = 100L;
        Document doc = new Document();
        doc.setId(docId);
        doc.setFilePath("some/path/file.pdf");

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        // --- ACT ---
        documentService.deleteDocument(docId);

        // --- ASSERT ---
        verify(documentChunkService).deleteAllChunksForDocument(docId);
        verify(fileStorageService).deleteFile(doc.getFilePath());
        verify(documentRepository).delete(doc);
    }
}

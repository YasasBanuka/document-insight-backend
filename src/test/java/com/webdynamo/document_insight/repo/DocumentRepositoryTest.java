package com.webdynamo.document_insight.repo;

import com.webdynamo.document_insight.model.Document;
import com.webdynamo.document_insight.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest // Focuses only on JPA components
class DocumentRepositoryTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TestEntityManager entityManager; // Helper for setting up test data

    @Test
    @DisplayName("findByUserId - Should only return documents belonging to that user")
    void findByUserId_ShouldFilterCorrectly() {
        // --- ARRANGE ---
        User user1 = new User();
        user1.setEmail("user1@example.com");
        user1.setPassword("pass");
        entityManager.persist(user1);

        User user2 = new User();
        user2.setEmail("user2@example.com");
        user2.setPassword("pass");
        entityManager.persist(user2);

        Document doc1 = new Document();
        doc1.setFilename("doc1.pdf");
        doc1.setUserId(user1.getId());
        doc1.setUploadedAt(LocalDateTime.now());
        documentRepository.save(doc1);

        Document doc2 = new Document();
        doc2.setFilename("doc2.pdf");
        doc2.setUserId(user2.getId());
        doc2.setUploadedAt(LocalDateTime.now());
        documentRepository.save(doc2);

        // --- ACT ---
        List<Document> result = documentRepository.findByUserId(user1.getId());

        // --- ASSERT ---
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFilename()).isEqualTo("doc1.pdf");
        assertThat(result.get(0).getUserId()).isEqualTo(user1.getId());
    }

    @Test
    @DisplayName("existsByFilename - Should return true if filename exists")
    void existsByFilename_ShouldWork() {
        // --- ARRANGE ---
        Document doc = new Document();
        doc.setFilename("unique.pdf");
        doc.setUploadedAt(LocalDateTime.now());
        documentRepository.save(doc);

        // --- ACT & ASSERT ---
        assertThat(documentRepository.existsByFilename("unique.pdf")).isTrue();
        assertThat(documentRepository.existsByFilename("non-existent.pdf")).isFalse();
    }
}

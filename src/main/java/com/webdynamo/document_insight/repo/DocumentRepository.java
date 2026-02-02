package com.webdynamo.document_insight.repo;

import com.webdynamo.document_insight.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByUserId(Long userId);

    List<Document> findByContentType(String contentType);

    boolean existsByFilename(String filename);
}

package com.webdynamo.document_insight.dto;

import com.webdynamo.document_insight.model.Document;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentDTO {

    private Long id;
    private String filename;
    private String contentType;
    private Long fileSize;
    private LocalDateTime uploadedAt;
    private Long chunkCount;

    public DocumentDTO(Document document, Long chunkCount) {
        this.id = document.getId();
        this.filename = document.getFilename();
        this.contentType = document.getContentType();
        this.fileSize = document.getFileSize();
        this.uploadedAt = document.getUploadedAt();
        this.chunkCount = chunkCount;
    }
}

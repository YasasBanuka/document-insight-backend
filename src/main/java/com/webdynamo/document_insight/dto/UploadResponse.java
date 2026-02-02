package com.webdynamo.document_insight.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UploadResponse {
    private Long documentId;
    private String filename;
    private String message;
    private Long fileSize;
    private String contentType;
}

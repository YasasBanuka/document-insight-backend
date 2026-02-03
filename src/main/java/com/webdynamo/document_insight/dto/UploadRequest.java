package com.webdynamo.document_insight.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UploadRequest {

    @NotNull(message = "File is required")
    private MultipartFile file;

    private Long userId = 1L;  // Default user
}

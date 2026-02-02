package com.webdynamo.document_insight.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "application.file-storage")
@Data
public class FileStorageConfig {
    private String uploadDir;
}

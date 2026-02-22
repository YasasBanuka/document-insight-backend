package com.webdynamo.document_insight.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {
    private Long id;
    private String type;  // "QUESTION" or "ANSWER"
    private String content;
    private List<RAGResponse.Source> sources;
    private LocalDateTime createdAt;
}

package com.webdynamo.document_insight.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RAGResponse {

    private String answer;
    private List<Source> sources;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Source {
        private String filename;
        private double similarity;
        private Long documentId;
    }
}

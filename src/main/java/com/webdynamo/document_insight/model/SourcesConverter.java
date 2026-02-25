package com.webdynamo.document_insight.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webdynamo.document_insight.dto.RAGResponse;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class SourcesConverter implements AttributeConverter<List<RAGResponse.Source>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<RAGResponse.Source> sources) {
        try {
            return sources == null ? null : objectMapper.writeValueAsString(sources);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting sources to JSON", e);
        }    }

    @Override
    public List<RAGResponse.Source> convertToEntityAttribute(String json) {
        try {
            return json == null ? null : objectMapper.readValue(json,
                    new TypeReference<List<RAGResponse.Source>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error parsing sources JSON", e);
        }
    }
}

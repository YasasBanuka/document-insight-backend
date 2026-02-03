package com.webdynamo.document_insight.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class QuestionRequest {
    @NotBlank(message = "Question cannot be empty")
    @Size(min = 3, max = 500, message = "Question must be between 3 and 500 characters")
    private String question;

    @Min(value = 1, message = "Context chunks must be at least 1")
    @Max(value = 10, message = "Context chunks cannot exceed 10")
    private int contextChunks = 3;
}

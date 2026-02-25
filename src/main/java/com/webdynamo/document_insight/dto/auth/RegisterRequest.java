package com.webdynamo.document_insight.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for user registration request
 * Contains only email and password required for creating new account
 */
public record RegisterRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @NotBlank(message = "Password us required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password

) {

}

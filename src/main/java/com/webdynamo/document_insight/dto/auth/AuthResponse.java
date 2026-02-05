package com.webdynamo.document_insight.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for authentication response
 * Returned after successful login/registration
 * Contains JWT tokens and user information
 */
public record AuthResponse(

        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("refresh_token")
        String refreshToken,

        @JsonProperty("user_id")
        Long userId,

        String email,

        String role,

        @JsonProperty("token_type")
        String tokenType
) {
    // Constructor with default token type
    public AuthResponse(
            String accessToken,
            String refreshToken,
            Long userId,
            String email,
            String role
    ) {
        this(accessToken, refreshToken, userId, email, role, "Bearer");
    }
}

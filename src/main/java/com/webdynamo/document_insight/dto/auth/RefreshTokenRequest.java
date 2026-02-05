package com.webdynamo.document_insight.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for refresh token request
 * Used to get a new access token using refresh token
 */
public record RefreshTokenRequest(

        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {
}

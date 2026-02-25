package com.webdynamo.document_insight.controller;

import com.webdynamo.document_insight.dto.auth.AuthResponse;
import com.webdynamo.document_insight.dto.auth.LoginRequest;
import com.webdynamo.document_insight.dto.auth.RefreshTokenRequest;
import com.webdynamo.document_insight.dto.auth.RegisterRequest;
import com.webdynamo.document_insight.model.User;
import com.webdynamo.document_insight.service.AuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationService authenticationService;

    /**
     * Register a new user
     * @param request Registration request with email and password
     * @return Authentication response with JWT tokens
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        log.info("Registration request received for email: {}", request.email());
        AuthResponse response = authenticationService.register(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Authenticate user and generate tokens
     * @param request Login request with email and password
     * @return Authentication response with JWT tokens
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        log.info("Login request received for email: {}", request.email());
        AuthResponse response = authenticationService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Refresh access token using refresh token
     * @param request Refresh token request
     * @return New authentication response with fresh tokens
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        log.info("Token refresh request received");
        AuthResponse response = authenticationService.refreshToken(request.refreshToken());
        return ResponseEntity.ok(response);
    }
}

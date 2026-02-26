package com.webdynamo.document_insight.service;

import com.webdynamo.document_insight.dto.auth.AuthResponse;
import com.webdynamo.document_insight.dto.auth.LoginRequest;
import com.webdynamo.document_insight.dto.auth.RegisterRequest;
import com.webdynamo.document_insight.model.Role;
import com.webdynamo.document_insight.model.User;
import com.webdynamo.document_insight.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    /**
     * Register a new user
     *
     * @param request Registration request with email and password
     * @return Authentication response with JWT tokens
     * @throws RuntimeException if email already exists
     */
    public AuthResponse register(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.email());

        // Check if user already exists
        if (userRepository.existsByEmail(request.email())) {
            log.warn("Registration failed: Email already exists - {}", request.email());
            throw new RuntimeException("Email already registered");
        }

        // Create new user
        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);

        // Save to database
        User savedUser = userRepository.save(user);
        log.info("User registered successfully with ID: {}", savedUser.getId());

        // Generate JWT tokens
        String accessToken = jwtService.generateToken(savedUser);
        String refreshToken = jwtService.generateRefreshToken(savedUser);

        // Return response
        return new AuthResponse(
                accessToken,
                refreshToken,
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getName(),
                savedUser.getRole().name()
        );
    }

    /**
     * Authenticate user and generate tokens
     *
     * @param request Login request with email and password
     * @return Authentication response with JWT tokens
     * @throws RuntimeException if authentication fails
     */
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.email());

        // Authenticate user (Spring Security handles password verification)
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()
                )
        );

        // If authentication succeeds, load user from database
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("User not found"));

        log.info("User authenticated successfully: {}", user.getEmail());

        // Generate JWT tokens
        String accessToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        // Return response
        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole().name()
        );
    }

    /**
     * Refresh access token using refresh token
     *
     * @param refreshToken Refresh token from client
     * @return New authentication response with fresh tokens
     * @throws RuntimeException if refresh token is invalid
     */
    public AuthResponse refreshToken(String refreshToken) {
        log.info("Refreshing access token");

        // Extract email from refresh token
        String userEmail = jwtService.extractEmail(refreshToken);

        // Load user from database
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Validate refresh token
        if (!jwtService.isTokenValid(refreshToken, user)) {
            log.warn("Invalid refresh token for user: {}", userEmail);
            throw new RuntimeException("Invalid refresh token");
        }

        log.info("Refresh token valid, generating new access token for user: {}", userEmail);

        // Generate new tokens
        String newAccessToken = jwtService.generateToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);

        // Return response with new tokens
        return new AuthResponse(
                newAccessToken,
                newRefreshToken,
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole().name()
        );
    }

    /**
     * Update user profile (name and email)
     *
     * @param userId User ID to update
     * @param newName New name (optional, can be same as current)
     * @param newEmail New email (optional, can be same as current)
     * @return Updated user
     * @throws RuntimeException if user not found or email already taken
     */
    public User updateUserProfile(Long userId, String newName, String newEmail) {
        log.info("Updating profile for user ID: {}", userId);

        // Find user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if email changed and if new email is available
        if (!user.getEmail().equals(newEmail)) {
            if (userRepository.existsByEmail(newEmail)) {
                log.warn("Email already in use: {}", newEmail);
                throw new RuntimeException("Email already in use");
            }
            log.info("Updating email from {} to {}", user.getEmail(), newEmail);
            user.setEmail(newEmail);
        }

        // Update name if provided
        if (newName != null && !newName.trim().isEmpty()) {
            user.setName(newName);
        }

        // Save and return
        User updatedUser = userRepository.save(user);
        log.info("Profile updated successfully for user: {}", userId);

        return updatedUser;
    }


}

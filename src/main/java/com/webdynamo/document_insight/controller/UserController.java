package com.webdynamo.document_insight.controller;

import com.webdynamo.document_insight.dto.UpdateProfileRequest;
import com.webdynamo.document_insight.model.User;
import com.webdynamo.document_insight.service.AuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final AuthenticationService authenticationService;

    /**
     * Get current user profile
     */
    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser(@AuthenticationPrincipal User user) {
        log.info("Fetching profile for user: {}", user.getId());
        return ResponseEntity.ok(user);
    }

    /**
     * Update user profile
     */
    @PutMapping("/profile")
    public ResponseEntity<User> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal User user
    ) {
        log.info("Updating profile for user: {}", user.getId());

        User updatedUser = authenticationService.updateUserProfile(
                user.getId(),
                request.name(),
                request.email()
        );

        return ResponseEntity.ok(updatedUser);
    }

}

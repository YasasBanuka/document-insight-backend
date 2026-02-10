package com.webdynamo.document_insight.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webdynamo.document_insight.dto.ErrorResponse;
import com.webdynamo.document_insight.model.User;
import com.webdynamo.document_insight.service.MetricsService;
import com.webdynamo.document_insight.service.RateLimitService;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final MeterRegistry meterRegistry;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Get authenticated user (if any)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Bucket bucket;
        String identifier;

        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof User user) {
            // Authenticated user - use user ID
            identifier = "User-" + user.getId();
            bucket = rateLimitService.resolveBucket(user.getId());

        } else {
            // Unauthenticated - use IP address
            String ipAddress = getClientIP(request);
            identifier = "IP-" + ipAddress;
            bucket = rateLimitService.resolveBucket(ipAddress);
        }

        // Try to consume 1 token
        if (rateLimitService.tryConsume(bucket)) {
            // Rate limit OK - proceed
            long remainingTokens = rateLimitService.getAvailableTokens(bucket);
            log.debug("[{}] Request allowed. Remaining tokens: {}", identifier, remainingTokens);

            // Metric: successful request
            metricsService.recordRateLimitAllowed(authentication != null
                    && authentication.isAuthenticated());

            // Add rate limit info to response headers
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(remainingTokens));

            filterChain.doFilter(request, response);

        } else {
            // Rate limit exceeded - return 429 with retry info
            log.warn("[{}] Rate limit exceeded for: {} {}", identifier, request.getMethod(), request.getRequestURI());

            // Metric: rate limit violation
            metricsService.recordRateLimitExceeded(
                    authentication != null && authentication.isAuthenticated(),
                    request.getRequestURI()
            );

            // Determine if authenticated
            boolean isAuthenticated = authentication != null
                    && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof User;

            // Calculate retry-after seconds
            long retryAfterSeconds = rateLimitService.getSecondsUntilRefill(bucket, isAuthenticated);

            // Create structured error response
            ErrorResponse errorResponse = new ErrorResponse(
                    LocalDateTime.now(),
                    429,
                    "Too Many Requests",
                    String.format("Rate limit exceeded. You can retry in %d seconds.", retryAfterSeconds),
                    request.getRequestURI(),
                    retryAfterSeconds
            );

            // Set response headers
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));  // Standard HTTP header

            // Write JSON response
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        }
    }

    /**
     * Get client IP address from request
     */
    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

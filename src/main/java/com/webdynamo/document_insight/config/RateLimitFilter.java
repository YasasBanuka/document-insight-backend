package com.webdynamo.document_insight.config;

import com.webdynamo.document_insight.model.User;
import com.webdynamo.document_insight.service.RateLimitService;
import io.github.bucket4j.Bucket;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

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

            // Add rate limit info to response headers
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(remainingTokens));

            filterChain.doFilter(request, response);

        } else {
            // Rate limit exceeded - return 429
            log.warn("[{}] Rate limit exceeded for: {} {}", identifier, request.getMethod(), request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Please try again later.\"}"
            );
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

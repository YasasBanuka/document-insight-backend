package com.webdynamo.document_insight.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webdynamo.document_insight.dto.ErrorResponse;
import com.webdynamo.document_insight.model.BucketType;
import com.webdynamo.document_insight.model.User;
import com.webdynamo.document_insight.service.MetricsService;
import com.webdynamo.document_insight.service.RateLimitService;
import com.webdynamo.document_insight.util.RequestUtils;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@Order(4)  // After JWT (Order 3), before general rate limit (Order 5)
@RequiredArgsConstructor
@Slf4j
public class RAGRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Only apply to RAG endpoints (/ask and POST /conversations)
        boolean isRagPath = path.contains("/ask") || 
                           (path.contains("/conversations") && (request.getMethod().equals("POST")));
        
        if (!isRagPath) {
            filterChain.doFilter(request, response);
            return;
        }

        // Get authenticated user from SecurityContext
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = null;
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof User) {
            user = (User) authentication.getPrincipal();
        }

        // Get bucket based on user or IP
        Bucket bucket;
        String identifier;
        
        if (user != null) {
            identifier = "User-" + user.getId();
            bucket = rateLimitService.getRAGBucket(user.getId().toString());
        } else {
            String ipAddress = RequestUtils.getClientIP(request);
            identifier = "IP-" + ipAddress;
            bucket = rateLimitService.getUnauthenticatedRAGBucket(ipAddress);
        }

        // Try to consume 1 token
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            // Rate limited
            log.warn("[{}] RAG rate limit exceeded for: {}", identifier);
            metricsService.recordRateLimitExceeded(user != null, path);
            long retryAfter = rateLimitService.getRetryAfterSeconds(BucketType.RAG, user != null);
            sendRateLimitError(response, retryAfter);        }
    }

    /**
     * Send 429 rate limit error response
     */
    private void sendRateLimitError(HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                429,
                "Too Many Requests",
                String.format("RAG query rate limit exceeded. You can retry in %d seconds.", retryAfterSeconds),
                "/ask",
                retryAfterSeconds
        );

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }


}

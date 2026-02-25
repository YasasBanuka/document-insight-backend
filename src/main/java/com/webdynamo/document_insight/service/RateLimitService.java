package com.webdynamo.document_insight.service;

import com.webdynamo.document_insight.model.BucketType;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class RateLimitService {

    @Value("${rate-limit.authenticated.capacity}")
    private int authenticatedCapacity;

    @Value("${rate-limit.authenticated.refill-tokens}")
    private int authenticatedRefillTokens;

    @Value("${rate-limit.authenticated.refill-duration}")
    private Duration authenticatedRefillDuration;

    @Value("${rate-limit.unauthenticated.capacity}")
    private int unauthenticatedCapacity;

    @Value("${rate-limit.unauthenticated.refill-tokens}")
    private int unauthenticatedRefillTokens;

    @Value("${rate-limit.unauthenticated.refill-duration}")
    private Duration unauthenticatedRefillDuration;

    @Value("${rate-limit.rag.authenticated.capacity}")
    private int ragAuthCapacity;

    @Value("${rate-limit.rag.authenticated.refill-tokens}")
    private int ragAuthRefillTokens;

    @Value("${rate-limit.rag.authenticated.refill-duration}")
    private Duration ragAuthRefillDuration;

    @Value("${rate-limit.rag.unauthenticated.capacity}")
    private int ragUnauthCapacity;

    @Value("${rate-limit.rag.unauthenticated.refill-tokens}")
    private int ragUnauthRefillTokens;

    @Value("${rate-limit.rag.unauthenticated.refill-duration}")
    private Duration ragUnauthRefillDuration;

    // Store buckets per user (in-memory)
    private final Map<String, Bucket> bucketCache = new ConcurrentHashMap<>();

    /**
     * Get or create bucket for authenticated user
     */
    public Bucket resolveBucket(Long userId) {
        String key = "user_" + userId;
        return bucketCache.computeIfAbsent(key, k -> createAuthenticatedBucket());
    }

    /**
     * Get or create bucket for unauthenticated request (by IP)
     */
    public Bucket resolveBucket(String ipAddress) {
        String key = "ip_" + ipAddress;
        return bucketCache.computeIfAbsent(key, k -> createUnauthenticatedBucket());
    }

    /**
     * Create bucket for authenticated users
     */
    private Bucket createAuthenticatedBucket() {
        Bandwidth limit = Bandwidth.classic(
                authenticatedCapacity,
                Refill.intervally(authenticatedRefillTokens, authenticatedRefillDuration)
        );
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Create bucket for unauthenticated users
     */
    private Bucket createUnauthenticatedBucket() {
        Bandwidth limit = Bandwidth.classic(
                unauthenticatedCapacity,
                Refill.intervally(unauthenticatedRefillTokens, unauthenticatedRefillDuration)
        );
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Try to consume 1 token
     * Returns true if allowed, false if rate limit exceeded
     */
    public boolean tryConsume(Bucket bucket) {
        return bucket.tryConsume(1);
    }

    /**
     * Get remaining tokens in bucket
     */
    public long getAvailableTokens(Bucket bucket) {
        return bucket.getAvailableTokens();
    }

    /**
     * Get seconds until bucket refills (for Retry-After header)
     * @deprecated Use {@link #getRetryAfterSeconds(BucketType, boolean)} instead
     */
    @Deprecated
    public long getSecondsUntilRefill(Bucket bucket, boolean isAuthenticated) {
        // If bucket has tokens available, no need to wait
        if (bucket.getAvailableTokens() > 0) {
            return 0;
        }

        // Otherwise, return the refill duration
        Duration refillDuration = isAuthenticated
                ? authenticatedRefillDuration
                : unauthenticatedRefillDuration;

        return refillDuration.getSeconds();
    }

    /**
     * Get or create RAG bucket for authenticated user
     */
    public Bucket getRAGBucket(String userId) {
        String key = "rag:" + userId;
        return bucketCache.computeIfAbsent(key, k -> {
            Bandwidth limit = Bandwidth.classic(
                    ragAuthCapacity,
                    Refill.intervally(ragAuthRefillTokens, ragAuthRefillDuration)
            );
            return Bucket.builder()
                    .addLimit(limit)
                    .build();
        });
    }

    /**
     * Get or create RAG bucket for unauthenticated request
     */
    public Bucket getUnauthenticatedRAGBucket(String ip) {
        String key = "rag:unauth:" + ip;
        return bucketCache.computeIfAbsent(key, k -> {
            Bandwidth limit = Bandwidth.classic(
                    ragUnauthCapacity,  // 5 RAG queries per minute
                    Refill.intervally(ragUnauthRefillTokens, ragUnauthRefillDuration)
            );
            return Bucket.builder()
                    .addLimit(limit)
                    .build();
        });
    }

    /**
     * Get retry-after duration in seconds for a given bucket type
     * This is the GENERIC method that all filters will use
     *
     * @param type BucketType (GENERAL or RAG)
     * @param isAuthenticated Whether user is authenticated
     * @return Seconds to wait before retrying
     */
    public long getRetryAfterSeconds(BucketType type, boolean isAuthenticated) {
        Duration duration = switch (type) {
            case GENERAL -> isAuthenticated
                    ? authenticatedRefillDuration
                    : unauthenticatedRefillDuration;

            case RAG -> isAuthenticated
                    ? ragAuthRefillDuration
                    : ragUnauthRefillDuration;
        };

        return duration.getSeconds();
    }
}

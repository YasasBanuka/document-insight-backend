package com.webdynamo.document_insight.service;

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
     */
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


}

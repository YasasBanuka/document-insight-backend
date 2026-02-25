package com.webdynamo.document_insight.service;

import com.webdynamo.document_insight.model.BucketType;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitServiceTest {

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService();

        // Since RateLimitService uses @Value on private fields, 
        // and we aren't loading the full Spring Context (to keep tests fast),
        // we use ReflectionTestUtils to manually set those values.
        ReflectionTestUtils.setField(rateLimitService, "authenticatedCapacity", 100);
        ReflectionTestUtils.setField(rateLimitService, "authenticatedRefillTokens", 100);
        ReflectionTestUtils.setField(rateLimitService, "authenticatedRefillDuration", Duration.ofMinutes(1));

        ReflectionTestUtils.setField(rateLimitService, "unauthenticatedCapacity", 10);
        ReflectionTestUtils.setField(rateLimitService, "unauthenticatedRefillTokens", 10);
        ReflectionTestUtils.setField(rateLimitService, "unauthenticatedRefillDuration", Duration.ofMinutes(1));

        ReflectionTestUtils.setField(rateLimitService, "ragAuthCapacity", 20);
        ReflectionTestUtils.setField(rateLimitService, "ragAuthRefillTokens", 20);
        ReflectionTestUtils.setField(rateLimitService, "ragAuthRefillDuration", Duration.ofMinutes(1));

        ReflectionTestUtils.setField(rateLimitService, "ragUnauthCapacity", 5);
        ReflectionTestUtils.setField(rateLimitService, "ragUnauthRefillTokens", 5);
        ReflectionTestUtils.setField(rateLimitService, "ragUnauthRefillDuration", Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("Should resolve bucket with high capacity for authenticated users")
    void resolveBucket_Authenticated_ShouldHaveHighCapacity() {
        // --- ACT ---
        Bucket bucket = rateLimitService.resolveBucket(1L);

        // --- ASSERT ---
        assertNotNull(bucket);
        assertEquals(100, bucket.getAvailableTokens());
    }

    @Test
    @DisplayName("Should resolve bucket with low capacity for unauthenticated users")
    void resolveBucket_Unauthenticated_ShouldHaveLowCapacity() {
        // --- ACT ---
        Bucket bucket = rateLimitService.resolveBucket("127.0.0.1");

        // --- ASSERT ---
        // Mentorship Note: Always check that the object IS NOT NULL before calling methods on it!
        assertNotNull(bucket, "Bucket should not be null for a valid IP");
        assertEquals(10,  bucket.getAvailableTokens(), "Unauthenticated users should get only 10 tokens");
    }

    @Test
    @DisplayName("Should return correct retry-after duration for RAG authenticated bucket")
    void getRetryAfterSeconds_RAG_Authenticated() {
        // --- ACT ---
        long seconds = rateLimitService.getRetryAfterSeconds(BucketType.RAG, true);

        // --- ASSERT ---
        // In setUp, we set ragAuthRefillDuration to 1 minute (60 seconds)
        assertEquals(60, seconds);
    }
}

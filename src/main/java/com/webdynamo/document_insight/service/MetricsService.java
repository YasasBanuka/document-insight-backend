package com.webdynamo.document_insight.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsService {

    private final MeterRegistry meterRegistry;

    /**
     * Record rate limit allowed
     */
    public void recordRateLimitAllowed(boolean isAuthenticated) {
        meterRegistry.counter("rate_limit.requests",
                "status", "allowed",
                "authenticated", String.valueOf(isAuthenticated)
        ).increment();
    }

    /**
     * Record rate limit exceeded
     */
    public void recordRateLimitExceeded(boolean isAuthenticated, String endpoint) {
        meterRegistry.counter("rate_limit.violations",
                "authenticated", String.valueOf(isAuthenticated),
                "endpoint", endpoint
        ).increment();

        log.warn("Rate limit exceeded - authenticated: {}, endpoint: {}", isAuthenticated, endpoint);
    }

    /**
     * Record document upload
     */
    public void recordDocumentUpload(String fileType, long sizeBytes) {
        meterRegistry.counter("documents.uploaded",
                "type", fileType
        ).increment();

        meterRegistry.summary("documents.size_bytes",
                "type", fileType
        ).record(sizeBytes);
    }

    /**
     * Record RAG query
     */
    public void recordRagQuery(int contextChunks) {
        meterRegistry.counter("rag.queries").increment();

        meterRegistry.summary("rag.context_chunks").record(contextChunks);
    }

}

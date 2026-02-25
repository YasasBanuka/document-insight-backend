package com.webdynamo.document_insight.model;

/**
 * Types of rate limit buckets
 */
public enum BucketType {
    GENERAL,    // Regular API calls
    RAG         // RAG queries
}

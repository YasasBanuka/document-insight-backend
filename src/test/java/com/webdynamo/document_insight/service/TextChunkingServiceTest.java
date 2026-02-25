package com.webdynamo.document_insight.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextChunkingServiceTest {

    private TextChunkingService chunkingService;

    @BeforeEach
    void setUp() {
        // Pure function service - no mocks needed! 
        chunkingService = new TextChunkingService();
    }

    @Test
    @DisplayName("Should return empty list for null or empty input")
    void chunkText_EmptyInput() {
        assertTrue(chunkingService.chunkText(null).isEmpty());
        assertTrue(chunkingService.chunkText("").isEmpty());
    }

    @Test
    @DisplayName("Should split large text into chunks with 200-character overlap")
    void chunkText_LargeText_Overlap() {
        // --- ARRANGE ---
        // Create a fake string of 2500 'A's
        String input = "A".repeat(2500);

        // --- ACT ---
        List<String> chunks = chunkingService.chunkText(input);

        // --- ASSERT ---
        // User's math was correct:
        // Chunk 1: 0 to 2000
        // Chunk 2: (2000 - 200 overlap) = 1800 to 2500
        assertEquals(2, chunks.size());
        assertEquals(2000, chunks.get(0).length());
        assertEquals(700, chunks.get(1).length()); // 2500 - 1800 = 700
    }

    @Test
    @DisplayName("Should attempt to break chunks at sentence boundaries")
    void chunkText_SentenceBoundary() {
        // --- ARRANGE ---
        // We create a string where the 2000th character is in the middle of a sentence,
        // but there is a ". " slightly before it.
        StringBuilder sb = new StringBuilder();
        sb.append("A".repeat(1950));
        sb.append(". "); // Sentence boundary at 1950 + 2 = 1952
        sb.append("B".repeat(100));
        
        String input = sb.toString();

        // --- ACT ---
        List<String> chunks = chunkingService.chunkText(input);

        // --- ASSERT ---
        // Instead of cutting exactly at 2000, it should cut at 1951 (after the dot)
        assertTrue(chunks.get(0).endsWith("."), "Chunk should end at the period, not split the 'B's");
        assertEquals(1951, chunks.get(0).length());
    }
}

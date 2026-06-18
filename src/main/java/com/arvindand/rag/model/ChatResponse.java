package com.arvindand.rag.model;

import java.time.Instant;
import java.util.List;

/**
 * Response model for chat interactions.
 *
 * @param content The AI-generated response content
 * @param conversationId The conversation ID for tracking
 * @param sources List of source documents used (if RAG was enabled)
 * @param timestamp Response generation timestamp
 * @author Arvind Menon
 */
public record ChatResponse(
    String content, String conversationId, List<String> sources, Instant timestamp) {
  /** Creates a response, defaulting the timestamp to now. */
  public static ChatResponse withSources(
      String content, String conversationId, List<String> sources) {
    return new ChatResponse(content, conversationId, sources, Instant.now());
  }
}

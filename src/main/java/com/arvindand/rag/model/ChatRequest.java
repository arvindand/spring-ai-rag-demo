package com.arvindand.rag.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Request model for chat interactions.
 *
 * <p>{@code conversationId} and {@code useRag} are optional; when omitted they default to {@code
 * default} and {@code true} respectively. {@code useRag} is a {@link Boolean} rather than a {@code
 * boolean} so a missing value binds to {@code null} (and is then defaulted) instead of failing
 * deserialization under Jackson 3.
 *
 * @param message The user's message/query
 * @param conversationId Optional conversation ID for multi-turn conversations
 * @param useRag Whether to use RAG (retrieval-augmented generation); defaults to {@code true}
 * @author Arvind Menon
 */
public record ChatRequest(
    @NotBlank(message = "Message cannot be blank") String message,
    String conversationId,
    Boolean useRag) {
  /** Applies defaults for any optional fields the caller omitted. */
  public ChatRequest {
    if (conversationId == null || conversationId.isBlank()) {
      conversationId = "default";
    }
    if (useRag == null) {
      useRag = true;
    }
  }
}

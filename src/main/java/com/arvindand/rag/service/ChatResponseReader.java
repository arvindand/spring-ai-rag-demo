package com.arvindand.rag.service;

import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.stereotype.Component;

/**
 * Reads the user-facing pieces out of a {@link ChatClientResponse}: the assistant text and the
 * distinct source filenames the RAG advisor retrieved.
 *
 * <p>Centralising this keeps the v1 (OpenAI-compatible) and v2 controllers consistent and free of
 * duplicated null-handling and metadata plumbing.
 *
 * @author Arvind Menon
 */
@Component
public class ChatResponseReader {

  /** Returns the assistant message text, or an empty string if the response carried none. */
  public String text(ChatClientResponse response) {
    var chatResponse = response.chatResponse();
    if (chatResponse == null) {
      return "";
    }
    var generation = chatResponse.getResult();
    if (generation == null) {
      return "";
    }
    String text = generation.getOutput().getText();
    return text == null ? "" : text;
  }

  /** Returns the sorted, distinct source filenames cited for a response (empty if none). */
  public List<String> sources(ChatClientResponse response) {
    if (!(response.context().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT)
        instanceof List<?> documents)) {
      return List.of();
    }
    return documents.stream()
        .filter(Document.class::isInstance)
        .map(Document.class::cast)
        .map(doc -> doc.getMetadata().get("source"))
        .filter(Objects::nonNull)
        .map(Object::toString)
        .map(ChatResponseReader::filenameOf)
        .distinct()
        .sorted()
        .toList();
  }

  private static String filenameOf(String source) {
    int slash = source.lastIndexOf('/');
    return slash < 0 ? source : source.substring(slash + 1);
  }
}

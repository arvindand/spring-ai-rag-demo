package com.arvindand.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Spring AI RAG Demo Application.
 *
 * <p>A demonstration of Retrieval-Augmented Generation (RAG) patterns using Spring AI 2.0,
 * featuring:
 *
 * <ul>
 *   <li>Modular RAG pipeline with query transformation
 *   <li>Conversation memory with JDBC persistence
 *   <li>Streaming responses via Server-Sent Events
 *   <li>Tool/function calling for document operations
 *   <li>Flexible document ingestion (PDF, Markdown, Text)
 * </ul>
 *
 * @author Arvind Menon
 * @see <a href="https://docs.spring.io/spring-ai/reference/">Spring AI Documentation</a>
 */
@SpringBootApplication
public class RAGApplication {

  private static final Logger LOG = LoggerFactory.getLogger(RAGApplication.class);

  public static void main(String[] args) {
    SpringApplication.run(RAGApplication.class, args);
  }

  /** Prints the available endpoints once the application is ready. */
  @Bean
  CommandLineRunner startupBanner() {
    return args -> {
      LOG.info("=".repeat(60));
      LOG.info("Spring AI RAG Demo - ready");
      LOG.info("=".repeat(60));
      LOG.info("API Endpoints:");
      LOG.info("  POST   /api/v2/chat         - Chat with RAG (returns cited sources)");
      LOG.info("  GET    /api/v2/chat/stream  - Streaming chat (SSE)");
      LOG.info("  POST   /api/v2/documents    - Upload a document to the knowledge base");
      LOG.info("  DELETE /api/v2/documents/{{id}} - Remove a document");
      LOG.info("  POST   /v1/chat/completions - OpenAI-compatible API (Open WebUI)");
      LOG.info("");
      LOG.info("To test with Open WebUI: docker compose --profile ui up -d  ->  http://localhost:3000");
      LOG.info("=".repeat(60));
    };
  }
}

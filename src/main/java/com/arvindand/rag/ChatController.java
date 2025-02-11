package com.arvindand.rag;

import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Enhanced REST controller for handling AI-powered chat interactions with specialized endpoints for
 * different types of financial queries.
 *
 * @author Arvind Menon
 */
@RestController
@RequestMapping("/api/v1/analysis")
class ChatController {

  private final ChatClient aiChatClient;

  private static final String BASE_SYSTEM_PROMPT =
      """
      You are a financial analyst assistant. When answering questions, always:
      1. Use specific numerical data from the document
      2. Provide clear comparisons when asked
      3. Explain underlying reasons for market movements
      4. Consider both direct and indirect effects
      """;

  private static final Map<String, String> SPECIALIZED_PROMPTS =
      Map.of(
          "factual",
          BASE_SYSTEM_PROMPT
              + "Focus on exact figures, dates, and specific events mentioned in the document. "
              + "Always include numerical values when available.",
          "analytical",
          BASE_SYSTEM_PROMPT
              + "Provide detailed comparative analysis by: \n"
              + "1. Citing specific performance numbers for each asset/sector\n"
              + "2. Explaining why different assets reacted differently\n"
              + "3. Highlighting contrasting movements\n"
              + "4. Discussing sector-specific impacts",
          "complex",
          BASE_SYSTEM_PROMPT
              + "Analyze interconnected relationships by: \n"
              + "1. Identifying direct cause-effect relationships\n"
              + "2. Explaining secondary effects\n"
              + "3. Highlighting market interconnections\n"
              + "4. Providing specific examples with data",
          "forward",
          BASE_SYSTEM_PROMPT
              + "Focus on future implications by: \n"
              + "1. Identifying specific risks mentioned\n"
              + "2. Explaining strategic considerations\n"
              + "3. Highlighting potential market impacts\n"
              + "4. Discussing suggested positioning");

  ChatController(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
    this.aiChatClient =
        chatClientBuilder
            .defaultAdvisors(
                new QuestionAnswerAdvisor(vectorStore, SearchRequest.builder().build()))
            .build();
  }

  /** Handles factual queries about specific data points and events. */
  @GetMapping("/factual")
  String handleFactualQuery(@RequestParam String query) {
    return generateResponse(query, "factual");
  }

  /** Handles analysis-based queries about market responses and sector performance. */
  @GetMapping("/analytical")
  String handleAnalyticalQuery(@RequestParam String query) {
    return generateResponse(query, "analytical");
  }

  /** Handles complex queries about relationships between different market factors. */
  @GetMapping("/complex")
  String handleComplexQuery(@RequestParam String query) {
    return generateResponse(query, "complex");
  }

  /** Handles forward-looking queries about implications and strategies. */
  @GetMapping("/forward")
  String handleForwardLookingQuery(@RequestParam String query) {
    return generateResponse(query, "forward");
  }

  private String generateResponse(String query, String queryType) {
    return aiChatClient
        .prompt()
        .system(SPECIALIZED_PROMPTS.get(queryType))
        .user("Using the provided document content, " + query)
        .call()
        .content();
  }
}

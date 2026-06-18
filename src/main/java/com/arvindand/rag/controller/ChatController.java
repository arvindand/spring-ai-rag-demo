package com.arvindand.rag.controller;

import com.arvindand.rag.model.ChatRequest;
import com.arvindand.rag.model.ChatResponse;
import com.arvindand.rag.service.ChatResponseReader;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * REST controller for chat interactions with advanced RAG capabilities.
 *
 * <ul>
 *   <li>{@code POST /api/v2/chat} — RAG or plain chat with conversation memory and source citations
 *   <li>{@code GET /api/v2/chat/stream} — streaming responses over Server-Sent Events
 * </ul>
 *
 * @author Arvind Menon
 */
@RestController
@RequestMapping("/api/v2/chat")
public class ChatController {

  private final ChatClient chatClient;
  private final ChatClient simpleChatClient;
  private final ChatResponseReader reader;

  public ChatController(
      @Qualifier("chatClient") ChatClient chatClient,
      @Qualifier("simpleChatClient") ChatClient simpleChatClient,
      ChatResponseReader reader) {
    this.chatClient = chatClient;
    this.simpleChatClient = simpleChatClient;
    this.reader = reader;
  }

  /**
   * Handles a chat request, optionally grounded in retrieved documents.
   *
   * @param request the message, conversation id, and RAG toggle
   * @return the assistant response, including cited sources when RAG is used
   */
  @PostMapping
  public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
    ChatClient client = request.useRag() ? chatClient : simpleChatClient;

    ChatClientResponse response =
        client
            .prompt()
            .user(request.message())
            .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, request.conversationId()))
            .call()
            .chatClientResponse();

    List<String> sources = request.useRag() ? reader.sources(response) : List.of();
    return ChatResponse.withSources(reader.text(response), request.conversationId(), sources);
  }

  /**
   * Streams an RAG-grounded response token-by-token over Server-Sent Events.
   *
   * @param message the user's message
   * @param conversationId optional conversation id (defaults to {@code default})
   * @return a stream of response content chunks
   */
  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<String> streamChat(
      @RequestParam String message,
      @RequestParam(defaultValue = "default") String conversationId) {
    return chatClient
        .prompt()
        .user(message)
        .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
        .stream()
        .content();
  }
}

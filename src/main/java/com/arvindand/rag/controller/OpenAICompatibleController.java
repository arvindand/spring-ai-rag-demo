package com.arvindand.rag.controller;

import com.arvindand.rag.service.ChatResponseReader;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

/**
 * OpenAI-compatible Chat Completions API, so chat UIs such as Open WebUI can talk to this service.
 *
 * <p>Exposes two models:
 *
 * <ul>
 *   <li><b>spring-ai-chat</b> — general conversation without RAG
 *   <li><b>spring-ai-rag</b> — document-grounded responses with source citations
 * </ul>
 *
 * <p>Streaming is selected by the {@code stream} field in the request body (per the OpenAI spec),
 * and chunks are serialised as proper Server-Sent Events rather than hand-assembled JSON. Each
 * request is stateless: its messages are the entire conversation, and Authorization is not used as
 * a conversation identifier.
 *
 * @author Arvind Menon
 */
@RestController
@RequestMapping("/v1")
public class OpenAICompatibleController {

  private static final String MODEL_RAG = "spring-ai-rag";
  private static final String MODEL_CHAT = "spring-ai-chat";

  private final ChatClient ragChatClient;
  private final ChatClient simpleChatClient;
  private final ChatResponseReader reader;

  public OpenAICompatibleController(
      @Qualifier("statelessRagChatClient") ChatClient ragChatClient,
      @Qualifier("statelessSimpleChatClient") ChatClient simpleChatClient,
      ChatResponseReader reader) {
    this.ragChatClient = ragChatClient;
    this.simpleChatClient = simpleChatClient;
    this.reader = reader;
  }

  /**
   * OpenAI-compatible chat completions. Returns a single JSON response, or an SSE stream of {@code
   * chat.completion.chunk} events when {@code stream} is {@code true}.
   */
  @PostMapping(
      value = "/chat/completions",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
  public Object chatCompletions(@RequestBody ChatCompletionRequest request) {

    Prompt prompt = toPrompt(request.messages());
    if (MODEL_RAG.equalsIgnoreCase(request.model())
        && prompt.getUserMessage().getText().isBlank()) {
      throw badRequest("RAG requests need a non-blank latest user message");
    }
    ChatClient client = selectClient(request.model());

    return Boolean.TRUE.equals(request.stream())
        ? stream(client, request.model(), prompt)
        : complete(client, request.model(), prompt);
  }

  private ChatCompletionResponse complete(ChatClient client, String model, Prompt prompt) {
    ChatClientResponse response = client.prompt(prompt).call().chatClientResponse();

    String content = reader.text(response);
    if (MODEL_RAG.equalsIgnoreCase(model)) {
      List<String> sources = reader.sources(response);
      if (!sources.isEmpty()) {
        content += "\n\n---\n**Sources:** " + String.join(", ", sources);
      }
    }
    return ChatCompletionResponse.of(model, content);
  }

  private Flux<ServerSentEvent<Object>> stream(ChatClient client, String model, Prompt prompt) {
    String id = "chatcmpl-" + shortId();
    long created = epochSeconds();

    return client.prompt(prompt).stream()
        .content()
        .map(chunk -> sse(ChatCompletionChunk.delta(id, created, model, chunk)))
        .concatWith(Flux.just(sse(ChatCompletionChunk.stop(id, created, model))))
        .concatWith(Flux.just(ServerSentEvent.<Object>builder().data("[DONE]").build()));
  }

  /** Lists available models (required by Open WebUI). */
  @GetMapping("/models")
  public ModelsResponse listModels() {
    long now = epochSeconds();
    return new ModelsResponse(
        "list",
        List.of(
            new Model(MODEL_CHAT, "model", now, "spring-ai"),
            new Model(MODEL_RAG, "model", now, "spring-ai")));
  }

  private ChatClient selectClient(String model) {
    return MODEL_RAG.equalsIgnoreCase(model) ? ragChatClient : simpleChatClient;
  }

  /** Converts the supported text-only roles without discarding or reordering the transcript. */
  private Prompt toPrompt(List<Message> messages) {
    if (messages == null || messages.isEmpty()) {
      throw badRequest("messages must contain at least one message");
    }
    List<org.springframework.ai.chat.messages.Message> transcript =
        messages.stream()
            .<org.springframework.ai.chat.messages.Message>map(
                message -> {
                  if (message == null || message.role() == null || message.content() == null) {
                    throw badRequest("Each message needs a role and text content");
                  }
                  return switch (message.role().toLowerCase(Locale.ROOT)) {
                    case "system" -> new SystemMessage(message.content());
                    case "user" -> new UserMessage(message.content());
                    case "assistant" -> new AssistantMessage(message.content());
                    default ->
                        throw badRequest("Supported message roles are system, user, and assistant");
                  };
                })
            .toList();
    return new Prompt(transcript);
  }

  private static ResponseStatusException badRequest(String detail) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
  }

  /** Keep request errors as 400 responses rather than the application's generic error handler. */
  @ExceptionHandler(ResponseStatusException.class)
  public ProblemDetail handleInvalidRequest(ResponseStatusException exception) {
    return exception.getBody();
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleUnreadableRequest(HttpMessageNotReadableException exception) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST,
        "Request must be valid JSON with messages containing a role and string content");
  }

  private static ServerSentEvent<Object> sse(Object data) {
    return ServerSentEvent.<Object>builder().data(data).build();
  }

  private static String shortId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private static long epochSeconds() {
    return System.currentTimeMillis() / 1000;
  }

  // --- Request/response DTOs following the OpenAI API spec (serialised by Jackson) ---

  public record ChatCompletionRequest(
      String model,
      List<Message> messages,
      Double temperature,
      Integer max_tokens,
      Boolean stream) {}

  public record Message(String role, String content) {}

  public record ChatCompletionResponse(
      String id, String object, long created, String model, List<Choice> choices, Usage usage) {
    public static ChatCompletionResponse of(String model, String content) {
      return new ChatCompletionResponse(
          "chatcmpl-" + shortId(),
          "chat.completion",
          epochSeconds(),
          model != null ? model : MODEL_CHAT,
          List.of(new Choice(0, new ResponseMessage("assistant", content), "stop")),
          new Usage(0, 0, 0));
    }
  }

  public record Choice(int index, ResponseMessage message, String finish_reason) {}

  public record ResponseMessage(String role, String content) {}

  public record Usage(int prompt_tokens, int completion_tokens, int total_tokens) {}

  public record ChatCompletionChunk(
      String id, String object, long created, String model, List<ChunkChoice> choices) {
    static ChatCompletionChunk delta(String id, long created, String model, String content) {
      return new ChatCompletionChunk(
          id,
          "chat.completion.chunk",
          created,
          model,
          List.of(new ChunkChoice(0, new Delta("assistant", content), null)));
    }

    static ChatCompletionChunk stop(String id, long created, String model) {
      return new ChatCompletionChunk(
          id,
          "chat.completion.chunk",
          created,
          model,
          List.of(new ChunkChoice(0, new Delta(null, null), "stop")));
    }
  }

  public record ChunkChoice(int index, Delta delta, String finish_reason) {}

  public record Delta(String role, String content) {}

  public record ModelsResponse(String object, List<Model> data) {
    public ModelsResponse {
      object = object != null ? object : "list";
      data = data != null ? data : List.of();
    }
  }

  public record Model(String id, String object, long created, String owned_by) {}
}

package com.arvindand.rag.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arvindand.rag.config.ChatClientConfig;
import com.arvindand.rag.model.ChatRequest;
import com.arvindand.rag.service.ChatResponseReader;
import com.arvindand.rag.tools.DocumentTools;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/** Uses the real client/advisor wiring with an in-process model; no database or API key needed. */
class OpenAIConversationTest {

  private AnnotationConfigApplicationContext context;
  private RecordingModel model;
  private OpenAICompatibleController controller;
  private InMemoryChatMemoryRepository repository;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    model = new RecordingModel();
    repository = new InMemoryChatMemoryRepository();
    context = new AnnotationConfigApplicationContext();
    context.registerBean(
        ChatClient.Builder.class,
        () -> ChatClient.builder(model),
        definition -> definition.setScope("prototype"));
    context.registerBean(
        org.springframework.ai.chat.memory.ChatMemory.class,
        () -> MessageWindowChatMemory.builder().chatMemoryRepository(repository).build());
    context.registerBean(VectorStore.class, StubVectorStore::new);
    context.register(
        ChatClientConfig.class,
        OpenAICompatibleController.class,
        ChatController.class,
        ChatResponseReader.class,
        DocumentTools.class);
    context.refresh();
    controller = context.getBean(OpenAICompatibleController.class);
    mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @AfterEach
  void closeContext() {
    context.close();
  }

  @Test
  void completionUsesTheEntireRequestTranscriptInOrder() {
    controller.chatCompletions(request(history(), false));

    assertThat(model.lastPrompt().getInstructions())
        .extracting(Message::getText)
        .containsExactly("Answer in French.", "My name is Alice.", "Bonjour Alice.", "My name?");
    assertThat(model.lastPrompt().getInstructions())
        .filteredOn(message -> message.getText().equals("Bonjour Alice."))
        .extracting(Message::getMessageType)
        .containsExactly(MessageType.ASSISTANT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"spring-ai-chat", "spring-ai-rag"})
  void independentCompletionsWithTheSameAuthorizationDoNotShareMemory(String chatModel)
      throws Exception {
    for (String text : List.of("My secret is apricot.", "What is 2+2?")) {
      mvc.perform(
              post("/v1/chat/completions")
                  .header("Authorization", "Bearer shared-key")
                  .contentType("application/json")
                  .content(
                      """
                      {"model":"%s","messages":[{"role":"user","content":"%s"}]}
                      """
                          .formatted(chatModel, text)))
          .andExpect(status().isOk());
    }

    assertThat(model.lastPrompt().getInstructions())
        .extracting(Message::getText)
        .noneMatch(text -> text.contains("My secret is apricot."));
    assertThat(model.lastPrompt().getInstructions())
        .filteredOn(message -> message.getMessageType() == MessageType.ASSISTANT)
        .isEmpty();
    assertThat(repository.findConversationIds()).isEmpty();
  }

  @Test
  void streamingUsesTheTranscriptAndDoesNotPersistIt() {
    @SuppressWarnings("unchecked")
    Flux<ServerSentEvent<Object>> events =
        (Flux<ServerSentEvent<Object>>) controller.chatCompletions(request(history(), true));

    StepVerifier.create(events)
        .assertNext(
            event ->
                assertThat(event.data())
                    .isInstanceOf(OpenAICompatibleController.ChatCompletionChunk.class))
        .assertNext(
            event -> {
              var chunk = (OpenAICompatibleController.ChatCompletionChunk) event.data();
              assertThat(chunk.choices().getFirst().finish_reason()).isEqualTo("stop");
            })
        .assertNext(event -> assertThat(event.data()).isEqualTo("[DONE]"))
        .verifyComplete();

    assertThat(model.lastPrompt().getInstructions())
        .extracting(Message::getText)
        .containsExactly("Answer in French.", "My name is Alice.", "Bonjour Alice.", "My name?");
    assertThat(repository.findConversationIds()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void ragPreservesHistoryWhileAddingDocumentContext(boolean streaming) {
    Object result =
        controller.chatCompletions(
            new OpenAICompatibleController.ChatCompletionRequest(
                "spring-ai-rag", history(), null, null, streaming));
    if (streaming) {
      ((Flux<?>) result).blockLast();
    } else {
      var response = (OpenAICompatibleController.ChatCompletionResponse) result;
      assertThat(response.choices().getFirst().message().content())
          .contains("**Sources:** faq.txt");
    }

    assertThat(model.lastPrompt().getInstructions())
        .extracting(Message::getText)
        .containsSubsequence("Answer in French.", "My name is Alice.", "Bonjour Alice.");
    assertThat(model.lastPrompt().getUserMessage().getText()).contains("Sample document context");
    assertThat(repository.findConversationIds()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"messages\":[]}",
        "{\"messages\":[null]}",
        "{\"messages\":[{\"content\":\"hello\"}]}",
        "{\"messages\":[{\"role\":\"user\"}]}",
        "{\"messages\":[{\"role\":\"tool\",\"content\":\"hello\"}]}",
        "{\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"hello\"}]}]}",
        "invalid-json"
      })
  void malformedOrUnsupportedTranscriptsReturn400BeforeCallingTheModel(String body)
      throws Exception {
    mvc.perform(post("/v1/chat/completions").contentType("application/json").content(body))
        .andExpect(status().isBadRequest());
    assertThat(model.prompts).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"role\":\"system\",\"content\":\"Answer briefly.\"}",
        "{\"role\":\"user\",\"content\":\"\"}",
        "{\"role\":\"user\",\"content\":\"   \"}",
        "{\"role\":\"user\",\"content\":\"Earlier question\"},{\"role\":\"user\",\"content\":\"\"}"
      })
  void ragRequiresANonBlankLatestUserTurn(String messages) throws Exception {
    for (boolean streaming : List.of(false, true)) {
      mvc.perform(
              post("/v1/chat/completions")
                  .contentType("application/json")
                  .content(
                      "{\"model\":\"spring-ai-rag\",\"stream\":"
                          + streaming
                          + ",\"messages\":["
                          + messages
                          + "]}"))
          .andExpect(status().isBadRequest());
    }
    assertThat(model.prompts).isEmpty();
  }

  @Test
  void plainChatPreservesSystemOnlyText() {
    controller.chatCompletions(request(List.of(message("system", "Offer a greeting.")), false));
    assertThat(model.lastPrompt().getInstructions())
        .extracting(Message::getText)
        .containsExactly("Offer a greeting.");
  }

  @Test
  void v2RetainsExplicitConversationMemoryAndSeparatesConversationIds() {
    ChatController v2 = context.getBean(ChatController.class);
    v2.chat(new ChatRequest("My name is Alice.", "alice", false));
    v2.chat(new ChatRequest("My name?", "alice", false));
    assertThat(model.lastPrompt().getInstructions())
        .extracting(Message::getText)
        .containsSubsequence("My name is Alice.", "Synthetic reply", "My name?");

    v2.chat(new ChatRequest("What is 2+2?", "bob", false));
    assertThat(model.lastPrompt().getInstructions())
        .extracting(Message::getText)
        .doesNotContain("My name is Alice.", "My name?");
    assertThat(repository.findConversationIds()).containsExactlyInAnyOrder("alice", "bob");
  }

  private static OpenAICompatibleController.ChatCompletionRequest request(
      List<OpenAICompatibleController.Message> messages, boolean stream) {
    return new OpenAICompatibleController.ChatCompletionRequest(
        "spring-ai-chat", messages, null, null, stream);
  }

  private static OpenAICompatibleController.Message message(String role, String content) {
    return new OpenAICompatibleController.Message(role, content);
  }

  private static List<OpenAICompatibleController.Message> history() {
    return List.of(
        message("system", "Answer in French."),
        message("user", "My name is Alice."),
        message("assistant", "Bonjour Alice."),
        message("user", "My name?"));
  }

  private static class RecordingModel implements ChatModel {
    private final List<Prompt> prompts = new ArrayList<>();

    @Override
    public ChatResponse call(Prompt prompt) {
      prompts.add(prompt);
      return new ChatResponse(List.of(new Generation(new AssistantMessage("Synthetic reply"))));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
      return Flux.defer(() -> Flux.just(call(prompt)));
    }

    Prompt lastPrompt() {
      return prompts.getLast();
    }
  }

  private static class StubVectorStore implements VectorStore {
    @Override
    public void add(List<Document> documents) {}

    @Override
    public void delete(List<String> ids) {}

    @Override
    public void delete(Filter.Expression expression) {}

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
      return List.of(new Document("Sample document context", Map.of("source", "faq.txt")));
    }
  }
}

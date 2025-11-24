# Spring AI Quick Reference

## ChatClient

The main entry point for AI interactions:

```java
String response = chatClient.prompt()
    .user("Your question")
    .call()
    .content();
```

With streaming:

```java
Flux<String> stream = chatClient.prompt()
    .user("Your question")
    .stream()
    .content();
```

## Advisors

Advisors intercept and modify chat requests/responses.

**RetrievalAugmentationAdvisor** — Adds RAG. Searches vector store, injects relevant docs into context.

**MessageChatMemoryAdvisor** — Adds conversation history from a ChatMemory implementation.

**SimpleLoggerAdvisor** — Logs requests and responses. Useful for debugging.

Chain them via `defaultAdvisors()`:

```java
ChatClient.builder(model)
    .defaultAdvisors(ragAdvisor, memoryAdvisor, loggerAdvisor)
    .build();
```

## Vector Stores

Spring AI abstracts vector stores behind `VectorStore` interface. Common operations:

```java
// Add documents
vectorStore.add(List.of(document1, document2));

// Search
List<Document> results = vectorStore.similaritySearch(
    SearchRequest.query("search term")
        .withTopK(5)
        .withSimilarityThreshold(0.7)
);

// Delete
vectorStore.delete(List.of("doc-id-1", "doc-id-2"));
```

## Document Loading

Built-in readers for common formats:

```java
// PDF
new PagePdfDocumentReader(resource)

// Markdown  
new MarkdownDocumentReader(resource)

// Plain text
new TextReader(resource)
```

## Tool Calling

Annotate methods with `@Tool`:

```java
@Tool(description = "Search documents by topic")
public String search(String query) {
    // implementation
}
```

Register tools with ChatClient:

```java
ChatClient.builder(model)
    .defaultTools(myToolBean)
    .build();
```

The LLM decides when to call tools based on your descriptions.

## Chat Memory

`MessageWindowChatMemory` keeps recent messages:

```java
MessageWindowChatMemory.builder()
    .chatMemoryRepository(jdbcRepository)  // or in-memory
    .maxMessages(20)
    .build();
```

Use with `MessageChatMemoryAdvisor`:

```java
new MessageChatMemoryAdvisor(chatMemory)
```

Pass conversation ID per request:

```java
chatClient.prompt()
    .user("Hi")
    .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, "user-123"))
    .call();
```

## Embedding Models

Generate embeddings for custom use:

```java
@Autowired
EmbeddingModel embeddingModel;

float[] vector = embeddingModel.embed("text to embed");
List<float[]> vectors = embeddingModel.embed(List.of("text1", "text2"));
```

## Supported Providers

**Chat Models**: OpenAI, Azure OpenAI, Anthropic, Google Vertex AI, Amazon Bedrock, Mistral, Ollama

**Vector Stores**: PGVector, Pinecone, Milvus, Chroma, Redis, Elasticsearch, Weaviate

**Embedding Models**: OpenAI, Cohere, HuggingFace, Ollama

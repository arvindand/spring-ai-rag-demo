# Spring AI RAG Demo

RAG implementation using Spring AI 2.0.0, PGVector, and OpenAI. Includes conversation memory, tool calling, streaming, and an OpenAI-compatible API for Open WebUI integration.

## Stack

- Spring Boot 4.1.0 / Spring AI 2.0.0
- Java 25 (virtual threads enabled)
- OpenAI GPT-4o-mini
- PostgreSQL 17 + PGVector
- Open WebUI (optional)

## Quick Start

```bash
# Set your OpenAI API key
export OPENAI_API_KEY=sk-your-key

# Start PostgreSQL + PGVector
docker compose up -d

# Run the app
./mvnw spring-boot:run
```

App runs on `localhost:8080`.

## Testing the API

### Health Check

```bash
curl http://localhost:8080/actuator/health
```

### Basic Chat (with RAG)

```bash
curl -X POST http://localhost:8080/api/v2/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is Spring AI?"}'
```

With conversation memory:

```bash
curl -X POST http://localhost:8080/api/v2/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "My name is Alice", "conversationId": "session-123"}'

curl -X POST http://localhost:8080/api/v2/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is my name?", "conversationId": "session-123"}'
```

### Streaming Chat

```bash
curl -N http://localhost:8080/api/v2/chat/stream?message=Explain+RAG+in+3+sentences
```

### Upload Documents

```bash
# Upload a file to the vector store (returns a documentId)
curl -X POST http://localhost:8080/api/v2/documents \
  -F "file=@/path/to/document.pdf"

# Upload several files at once
curl -X POST http://localhost:8080/api/v2/documents/batch \
  -F "files=@/path/to/a.pdf" -F "files=@/path/to/b.md"

# Delete a document and all its chunks by id
curl -X DELETE http://localhost:8080/api/v2/documents/{documentId}
```

### OpenAI-Compatible API

Two models available:

- `spring-ai-chat` — General conversation (no RAG)
- `spring-ai-rag` — Uses uploaded documents for context, includes source citations

```bash
# List available models
curl http://localhost:8080/v1/models

# General chat (no RAG)
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer my-session" \
  -d '{
    "model": "spring-ai-chat",
    "messages": [{"role": "user", "content": "What is 2+2?"}]
  }'

# RAG-enabled chat (uses documents, shows sources)
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer my-session" \
  -d '{
    "model": "spring-ai-rag",
    "messages": [{"role": "user", "content": "What is Spring AI?"}]
  }'
# Response includes: "---\n**Sources:** faq.txt, spring-ai-reference.md"

# Streaming response
curl -N -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "spring-ai-chat",
    "messages": [{"role": "user", "content": "Tell me a joke"}],
    "stream": true
  }'
```

Memory persists per Authorization header — same header = same conversation.

## Open WebUI Integration

```bash
# Start Open WebUI alongside PostgreSQL
docker compose --profile ui up -d
```

Open `http://localhost:3000`. Auth is disabled (`WEBUI_AUTH=false`) and the connection
to this app's `/v1` API is pre-configured in `compose.yaml`, so the models appear
automatically — just pick `spring-ai-chat` (general) or `spring-ai-rag`
(document-grounded) and start chatting.

## Architecture

```text
Request ─┬─ ChatController             (/api/v2/chat, /stream)
         └─ OpenAICompatibleController  (/v1/chat/completions, Open WebUI)
                       │
                       ▼
                   ChatClient
                    ├── RetrievalAugmentationAdvisor (query rewrite + vector search)
                    ├── MessageChatMemoryAdvisor      (conversation history)
                    └── DocumentTools                 (@Tool functions)
                           │
         ┌─────────────────┼──────────────────┐
         ▼                 ▼                  ▼
  OpenAI / OpenRouter   PGVector          PostgreSQL
  (chat + embeddings)   (vectors)         (chat memory)
```

## Project Layout

```text
src/main/java/com/arvindand/rag/
├── RAGApplication.java
├── config/
│   ├── ChatClientConfig.java          # ChatClient + advisors + tools
│   └── MemoryConfig.java              # JDBC chat memory
├── controller/
│   ├── ChatController.java            # /api/v2/chat (+ /stream)
│   ├── DocumentController.java        # /api/v2/documents
│   ├── OpenAICompatibleController.java # /v1 OpenAI-compatible API
│   └── GlobalExceptionHandler.java    # RFC 9457 ProblemDetail errors
├── model/                            # request/response records
│   ├── ChatRequest.java
│   ├── ChatResponse.java
│   └── DocumentUploadResponse.java
├── service/
│   ├── DocumentService.java           # document ingestion pipeline
│   └── ChatResponseReader.java        # extracts assistant text + sources
└── tools/
    └── DocumentTools.java             # @Tool methods (wired via defaultTools)
```

## Key Patterns

**RetrievalAugmentationAdvisor** — The modular RAG approach in Spring AI 2.0. Rewrites the query, searches the vector store for relevant chunks, and injects them into the prompt context. Retrieved sources are surfaced back to the caller.

**MessageWindowChatMemory** — Keeps the last N messages per conversation, persisted to PostgreSQL via JDBC.

**@Tool annotation** — Methods the LLM can invoke. `DocumentTools` is registered on the ChatClient via `defaultTools(...)`, and Spring AI generates the JSON schema and handles the function-calling protocol.

## Config

```yaml
spring:
  threads:
    virtual:
      enabled: true          # Java 21+ virtual threads
  ai:
    openai:
      chat:
        model: gpt-4o-mini   # flattened in Spring AI 2.0 (was chat.options.model)
        temperature: 0.7
    vectorstore:
      pgvector:
        dimensions: 1536
        index-type: HNSW
```

## Using OpenRouter (or any OpenAI-compatible provider)

Spring AI 2.0 wraps the official OpenAI Java SDK, so the app can point at any
OpenAI-compatible endpoint. An `openrouter` profile is included
(`application-openrouter.yaml`):

```bash
# Provide the key via a local .env file (git-ignored): OPENROUTER_API_KEY=sk-or-...
./mvnw spring-boot:run --spring.profiles.active=openrouter

# ...or point ENV_FILE at a .env elsewhere:
ENV_FILE=/path/to/.env ./mvnw spring-boot:run --spring.profiles.active=openrouter
```

The profile overrides:

- `base-url: https://openrouter.ai/api/v1` — note the SDK base-url **includes** `/v1`
- `chat.model: openai/gpt-4o-mini` — OpenRouter uses provider-prefixed model ids
- `embedding.model: openai/text-embedding-3-small` — keep a **1536-dim** model so it
  matches `spring.ai.vectorstore.pgvector.dimensions` (a mismatch fails on insert)

The same applies to other gateways (Groq, NVIDIA, local model runners) — just change
`base-url`, `api-key`, and the model ids.

## Links

- [Spring AI Docs](https://docs.spring.io/spring-ai/reference/)
- [PGVector](https://github.com/pgvector/pgvector)
- [Open WebUI](https://github.com/open-webui/open-webui)

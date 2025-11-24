# Spring AI RAG Demo

RAG implementation using Spring AI 1.1.0, PGVector, and OpenAI. Includes conversation memory, tool calling, streaming, and an OpenAI-compatible API for Open WebUI integration.

## Stack

- Spring Boot 3.5.8 / Spring AI 1.1.0
- OpenAI GPT-4o-mini
- PostgreSQL 16 + PGVector
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
# Upload a file to the vector store
curl -X POST http://localhost:8080/api/v2/documents \
  -F "file=@/path/to/document.pdf"

# Clear all documents
curl -X DELETE http://localhost:8080/api/v2/documents
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

Open `http://localhost:3000`, create an account, then:

1. Go to **Settings → Admin → Connections**
2. Add OpenAI connection: `http://host.docker.internal:8080/v1`
3. Select model: `spring-ai-chat` (general) or `spring-ai-rag` (document-grounded)

## Architecture

```text
Request → ChatController → ChatClient
                              ├── RetrievalAugmentationAdvisor (RAG)
                              ├── MessageChatMemoryAdvisor (conversation history)
                              └── DocumentTools (@Tool functions)
                                      │
              ┌─────────────────────┼─────────────────────┐
              ▼                     ▼                     ▼
           OpenAI              PGVector              PostgreSQL
        (GPT-4o-mini)         (vectors)            (chat memory)
```

## Project Layout

```text
src/main/java/com/arvindand/rag/
├── config/
│   ├── ChatClientConfig.java    # ChatClient + advisors
│   └── MemoryConfig.java        # JDBC chat memory
├── controller/
│   ├── ChatController.java      # /api/v2/chat
│   ├── DocumentController.java  # /api/v2/documents
│   └── OpenAICompatibleController.java
├── service/
│   └── DocumentService.java     # document ingestion
└── tools/
    └── DocumentTools.java       # @Tool methods
```

## Key Patterns

**RetrievalAugmentationAdvisor** — The 1.1 approach to RAG. Searches the vector store for relevant chunks and injects them into the prompt context.

**MessageWindowChatMemory** — Keeps the last N messages per conversation, persisted to PostgreSQL via JDBC.

**@Tool annotation** — Methods the LLM can invoke. Spring AI generates the JSON schema and handles the function calling protocol.

## Config

```yaml
spring:
  ai:
    openai:
      chat:
        options:
          model: gpt-4o-mini
          temperature: 0.7
    vectorstore:
      pgvector:
        dimensions: 1536
        index-type: hnsw
```

## Links

- [Spring AI Docs](https://docs.spring.io/spring-ai/reference/)
- [PGVector](https://github.com/pgvector/pgvector)
- [Open WebUI](https://github.com/open-webui/open-webui)

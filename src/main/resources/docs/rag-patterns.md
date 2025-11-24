# RAG Implementation Patterns

## Basic RAG Flow

1. User asks a question
2. Embed the question
3. Search vector store for similar document chunks
4. Inject retrieved chunks into the prompt
5. LLM generates answer using the context

## Chunking

Documents need to be split into chunks before embedding. Tradeoffs:

- **Smaller chunks** (200-500 tokens): More precise retrieval, but may lose context
- **Larger chunks** (800-1500 tokens): Better context, but may include irrelevant content

Use overlap (10-20%) to avoid losing information at boundaries.

## Retrieval Parameters

**topK** — Number of chunks to retrieve. Start with 3-5, increase for complex queries.

**similarityThreshold** — Minimum relevance score (0-1). Higher = more relevant but fewer results. Start around 0.7.

## Query Strategies

**Direct query**: Embed the user's question as-is. Simple, works well for specific questions.

**Query expansion**: Add related terms before searching. Helps with ambiguous queries.

**HyDE** (Hypothetical Document Embeddings): Generate a hypothetical answer, embed that, then search. Counterintuitive but effective for some use cases.

## Context Window Management

LLMs have token limits. Budget your context:

```
System prompt:     ~500 tokens
Retrieved docs:   ~3000-4000 tokens  
Chat history:     ~1500 tokens
User query:        ~200 tokens
Response buffer:  ~1000+ tokens
```

If you exceed limits, either reduce topK, summarize history, or compress retrieved content.

## Metadata Filtering

Store metadata with documents (source, date, category). Filter during retrieval:

```java
SearchRequest.query("question")
    .withFilterExpression("category == 'technical'")
    .withTopK(5);
```

## Re-ranking

Retrieved results are ordered by embedding similarity, which isn't always optimal. Consider:

1. Retrieve more results than needed (e.g., topK=20)
2. Re-rank with a cross-encoder or LLM
3. Take top N after re-ranking

## Evaluation

Measure RAG quality with:

- **Retrieval**: Are the right documents being found?
- **Faithfulness**: Is the answer supported by retrieved context?
- **Relevance**: Does the answer address the question?

Test with known question-answer pairs from your documents.

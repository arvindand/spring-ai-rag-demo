# Spring AI RAG Demo

This project is a demonstration of implementing Retrieval Augmented Generation (RAG) using Spring AI and OpenAI's GPT models. It showcases how to enable intelligent document querying by leveraging the capabilities of Large Language Models (LLMs) alongside local document context.

```mermaid
flowchart TB
    subgraph DocumentPipeline["📄 Document Processing Pipeline"]
        direction TB
        A[("Raw PDF Document")] --> B[Text Extraction]
        B --> C[Chunk Splitting]
        C --> D[Embedding Generation]
        D --> E[(Vector Store)]
    end

    subgraph QueryPipeline["❓ Query Processing Pipeline"]
        direction TB
        F[("User Question")] --> G[Query Embedding]
        G --> H[Semantic Search]
        H --> I[Top-K Relevant Chunks]
        I --> J[LLM Synthesis]
        J --> K[("Final Answer")]
    end

    E <-.-> H

    style DocumentPipeline fill:#f8fafc,stroke:#1e3a8a
    style QueryPipeline fill:#f0fdf4,stroke:#14532d
    style A fill:#e0f2fe,stroke:#0369a1
    style E fill:#dbeafe,stroke:#1d4ed8
    style F fill:#dcfce7,stroke:#15803d
    style K fill:#dcfce7,stroke:#15803d
    style J fill:#fef9c3,stroke:#eab308
```
## Overview

This project illustrates how to:
- Import PDF documents into a vector database
- Conduct semantic searches with Spring AI
- Enhance LLM responses with pertinent document context
- Develop an API endpoint for document-aware chat interactions

## Getting Started

1. Set up your environment variables:
```properties
OPENAI_API_KEY=your_api_key_here
```

## Running the Application

1. Start Docker Desktop.

2. Run the application:
```bash
./mvnw spring-boot:run
```

The application will:
- Start a PostgreSQL database with the PGVector extension
- Initialize the vector store schema
- Ingest documents from the specified location
- Start a web server on port 8080

## Key Components

### DocumentIngestionService

The `DocumentIngestionService` is responsible for processing documents and populating the vector store.

The sample PDF document used for this sample is placed in the `src/main/resources/docs` directory.

### ChatController

The `ChatController` provides a REST endpoint for querying the document and generating insights.

## Testing / Example Queries

### Factual Queries
Get specific information about monetary policy decisions:

> What was the Federal Reserve interest rate cut?

```bash
curl "http://localhost:8080/api/v1/analysis/factual?query=What%20was%20the%20Federal%20Reserve%20interest%20rate%20cut?"
```

### Analytical Queries 
Compare performance across different market sectors:

> Compare the performance of REITs versus bank stocks

```bash
curl "http://localhost:8080/api/v1/analysis/analytical?query=Compare%20the%20performance%20of%20REITs%20versus%20bank%20stocks"
```

### Complex Relationship Queries
CUnderstand interconnected market impacts:

> How did the rate cut affect both dollar and emerging markets?

```bash
curl "http://localhost:8080/api/v1/analysis/complex?query=How%20did%20the%20rate%20cut%20affect%20both%20dollar%20and%20emerging%20markets?"
```

### Forward-Looking Queries
Identify potential risks and future implications:

> What are the main risk factors identified?

```bash
curl "http://localhost:8080/api/v1/analysis/forward?query=What%20are%20the%20main%20risk%20factors%20identified?"
```

> **Note:** To see only the response content:

**Windows PowerShell:**
```powershell
curl "http://localhost:8080/api/v1/analysis/factual?query=What%20was%20the%20rate%20cut?" | Select-Object -ExpandProperty Content
```

**Unix/Linux/MacOS:**
```bash
curl "http://localhost:8080/api/v1/analysis/factual?query=What%20was%20the%20rate%20cut?" -s | jq -r '.content'
```

### License

MIT License - See [LICENSE](/LICENSE) for details.
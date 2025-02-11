package com.arvindand.rag;

import static java.util.Optional.ofNullable;

import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Service responsible for ingesting PDF documents into a vector store. This service runs
 * automatically on application startup and ensures that PDF content is properly vectorized and
 * stored for later retrieval.
 *
 * @author Arvind Menon
 */
@Component
class DocumentIngestionService implements CommandLineRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentIngestionService.class);
  private final VectorStore documentStore;

  @Value("classpath:/docs/Financial_Newsletter.pdf")
  private Resource documentResource;

  /**
   * Constructs a new DocumentIngestionService with the specified vector store.
   *
   * @param documentStore the vector store where document embeddings will be stored
   */
  DocumentIngestionService(VectorStore documentStore) {
    this.documentStore = documentStore;
  }

  /**
   * Executes the document ingestion process on application startup. Checks if the vector store is
   * empty before proceeding with ingestion.
   *
   * @param args command line arguments (not used)
   * @throws Exception if any error occurs during the ingestion process
   */
  @Override
  public void run(String... args) throws Exception {
    LOGGER.info("Checking if document store is already populated...");

    if (isDocumentStoreEmpty()) {
      LOGGER.info("Document store is empty. Starting document ingestion...");
      ingestDocument();
    } else {
      LOGGER.info("Document store already contains data. Skipping ingestion.");
    }
  }

  /**
   * Checks if the document store is empty by performing a minimal similarity search.
   *
   * @return true if the document store is empty, false otherwise
   */
  private boolean isDocumentStoreEmpty() {
    try {
      // Use a simple dummy text for the check
      SearchRequest searchRequest =
          SearchRequest.builder().query("test").topK(1).similarityThreshold(0.0).build();
      List<?> searchResults =
          ofNullable(documentStore.similaritySearch(searchRequest)).orElse(Collections.emptyList());
      return searchResults.isEmpty();
    } catch (Exception e) {
      LOGGER.error("Error checking document store contents: {}", e.getMessage(), e);
      return true;
    }
  }

  /**
   * Processes and ingests the PDF document into the vector store. The document is split into
   * manageable chunks before ingestion.
   *
   * @throws Exception if any error occurs during document processing or ingestion
   */
  private void ingestDocument() throws Exception {
    PagePdfDocumentReader documentReader = new PagePdfDocumentReader(documentResource);
    TextSplitter documentSplitter = new TokenTextSplitter();
    documentStore.accept(documentSplitter.apply(documentReader.get()));
    LOGGER.info("Document successfully ingested into the vector store.");
  }
}

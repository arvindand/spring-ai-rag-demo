package com.arvindand.rag.service;

import com.arvindand.rag.model.DocumentUploadResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for document ingestion and management.
 *
 * <p>Supports PDF, Markdown, and plain-text documents. A single {@link #ingest} pipeline parses the
 * source into {@link Document}s, tags them with metadata, splits them into chunks, and stores the
 * chunks in the vector store for retrieval-augmented generation.
 *
 * @author Arvind Menon
 */
@Service
public class DocumentService {

  private static final Logger LOG = LoggerFactory.getLogger(DocumentService.class);

  static final String META_DOCUMENT_ID = "document_id";
  static final String META_FILENAME = "filename";
  static final String META_SOURCE = "source";

  private final VectorStore vectorStore;

  // Smaller chunks than the 800-token default: each topic embeds sharply, which
  // markedly improves retrieval recall on short, multi-topic documents.
  private final TokenTextSplitter textSplitter =
      TokenTextSplitter.builder().withChunkSize(200).withMinChunkSizeChars(100).build();

  public DocumentService(VectorStore vectorStore) {
    this.vectorStore = vectorStore;
  }

  /**
   * Uploads and processes an uploaded multipart file.
   *
   * @param file the multipart file to ingest
   * @return a response describing the outcome (never throws on parse/storage failure)
   */
  public DocumentUploadResponse uploadDocument(MultipartFile file) {
    String filename = file.getOriginalFilename();
    String documentId = UUID.randomUUID().toString();
    try {
      int chunks = ingest(file.getResource(), filename, documentId);
      LOG.info("Ingested document '{}' as {} chunks", filename, chunks);
      return DocumentUploadResponse.success(documentId, filename, chunks);
    } catch (Exception e) {
      LOG.error("Failed to ingest document '{}'", filename, e);
      return DocumentUploadResponse.failure(filename, e.getMessage());
    }
  }

  /**
   * Deletes every chunk belonging to a document.
   *
   * @param documentId the document id assigned at ingestion time
   */
  public void deleteDocument(String documentId) {
    vectorStore.delete("%s == '%s'".formatted(META_DOCUMENT_ID, documentId));
    LOG.info("Deleted document '{}'", documentId);
  }

  /**
   * The single ingestion pipeline shared by every entry point: read, tag, split, store.
   *
   * @return the number of chunks written to the vector store
   */
  int ingest(Resource resource, String filename, String documentId) {
    List<Document> documents = read(resource, filename);
    documents.forEach(
        doc -> {
          doc.getMetadata().put(META_DOCUMENT_ID, documentId);
          doc.getMetadata().put(META_FILENAME, filename);
          doc.getMetadata().put(META_SOURCE, filename);
        });

    List<Document> chunks = textSplitter.apply(documents);
    vectorStore.add(chunks);
    return chunks.size();
  }

  /** Selects a reader based on the file extension; anything unknown is treated as plain text. */
  private List<Document> read(Resource resource, String filename) {
    try {
      return switch (extensionOf(filename)) {
        case "pdf" -> new PagePdfDocumentReader(resource).get();
        case "md", "markdown" ->
            new MarkdownDocumentReader(resource, MarkdownDocumentReaderConfig.defaultConfig()).get();
        default -> List.of(new Document(resource.getContentAsString(StandardCharsets.UTF_8)));
      };
    } catch (IOException | RuntimeException e) {
      throw new IllegalStateException("Could not read '" + filename + "': " + e.getMessage(), e);
    }
  }

  private static String extensionOf(String filename) {
    if (filename == null) {
      return "";
    }
    int dot = filename.lastIndexOf('.');
    return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
  }
}

package com.selfhealing.backend.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocuments;

@Service
public class RunbookIngestionService {

    private static final Logger log = LoggerFactory.getLogger(RunbookIngestionService.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public RunbookIngestionService(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    @PostConstruct
    public void ingestRunbooks() {
        log.info("Starting Runbook Ingestion for RAG pipeline...");

        // We assume docs/runbooks/ exists in the root of the project. 
        // Adjust path if running from a different working directory.
        File runbookDir = new File("../docs/runbooks");
        if (!runbookDir.exists() || !runbookDir.isDirectory()) {
            runbookDir = new File("docs/runbooks"); // Fallback for local execution
            if (!runbookDir.exists()) {
                log.warn("Runbooks directory not found. Skipping RAG ingestion.");
                return;
            }
        }

        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:**/*.md");

        List<Document> documents = loadDocuments(runbookDir.toPath(), pathMatcher, new TextDocumentParser());

        if (documents.isEmpty()) {
            log.warn("No markdown runbooks found in {}", runbookDir.getAbsolutePath());
            return;
        }

        EmbeddingStoreIngestor ingestor = buildIngestor();
        ingestor.ingest(documents);

        log.info("Successfully ingested {} runbooks into the Vector Store.", documents.size());
    }

    /**
     * Ingest a single file into the vector store at runtime.
     * Called by RunbookGeneratorService after generating a new runbook.
     */
    public void ingestFile(Path filePath) {
        log.info("Ingesting new runbook file into vector store: {}", filePath);
        try {
            List<Document> documents = loadDocuments(filePath.getParent(),
                    p -> p.getFileName().toString().equals(filePath.getFileName().toString()),
                    new TextDocumentParser());

            if (documents.isEmpty()) {
                log.warn("No document loaded from path: {}", filePath);
                return;
            }

            EmbeddingStoreIngestor ingestor = buildIngestor();
            ingestor.ingest(documents);
            log.info("✅ Successfully ingested new runbook: {}", filePath.getFileName());
        } catch (Exception e) {
            log.error("Failed to ingest runbook file {}: {}", filePath, e.getMessage(), e);
        }
    }

    private EmbeddingStoreIngestor buildIngestor() {
        DocumentSplitter documentSplitter = DocumentSplitters.recursive(500, 50);
        return EmbeddingStoreIngestor.builder()
                .documentSplitter(documentSplitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
    }
}

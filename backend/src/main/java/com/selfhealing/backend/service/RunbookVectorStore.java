package com.selfhealing.backend.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RunbookVectorStore — Performs RAG Retrieval using LangChain4j.
 */
@Service
public class RunbookVectorStore {

    private static final Logger log = LoggerFactory.getLogger(RunbookVectorStore.class);

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public RunbookVectorStore(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    /**
     * Search the vector database for relevant runbooks based on semantic search.
     */
    public String searchSimilarIncidents(String incidentTitle, String affectedService) {
        String query = "Find runbooks, postmortems, or mitigation steps for service: " + affectedService + " related to: " + incidentTitle;
        log.info("RAG: Querying vector store for: '{}'", query);

        // 1. Embed the query
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // 2. Search vector store for top 3 most relevant segments (min score 0.6)
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(queryEmbedding, 3, 0.6);

        if (matches.isEmpty()) {
            return "No highly similar past incidents or runbooks found in knowledge base.";
        }

        // 3. Construct context
        String context = matches.stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.joining("\n---\n"));

        log.info("RAG: Found relevant context snippets.");
        return context;
    }

    /**
     * Search with score — returns the highest similarity score found.
     * Used by RunbookGeneratorService to determine if an incident is "novel"
     * (i.e., not covered by existing runbooks).
     *
     * @return the highest similarity score (0.0 to 1.0), or 0.0 if no matches found
     */
    public double getMaxSimilarityScore(String incidentTitle, String affectedService) {
        String query = "Runbook for service: " + affectedService + " failure: " + incidentTitle;
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // Search with a very low threshold to get any possible matches
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(queryEmbedding, 1, 0.0);

        if (matches.isEmpty()) {
            return 0.0;
        }

        double maxScore = matches.get(0).score();
        log.info("RAG: Max similarity score for '{}' / '{}' = {}", incidentTitle, affectedService, maxScore);
        return maxScore;
    }
}

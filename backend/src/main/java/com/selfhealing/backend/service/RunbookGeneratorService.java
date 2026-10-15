package com.selfhealing.backend.service;

import com.selfhealing.backend.model.Incident;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * RunbookGeneratorService — Dynamically generates new Markdown runbooks
 * when the system encounters a novel failure pattern not covered by existing runbooks.
 *
 * Workflow:
 * 1. Query the vector store to check if existing runbooks cover this incident
 * 2. If similarity score is below threshold → this is a "novel" incident
 * 3. Use the LLM to generate a new runbook in Markdown format
 * 4. Write the file to docs/runbooks/
 * 5. Re-ingest the new file into the EmbeddingStore for future RAG lookups
 * 6. Log an audit entry
 */
@Service
public class RunbookGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(RunbookGeneratorService.class);

    /**
     * If the max similarity score for an incident is below this threshold,
     * the incident is considered "novel" and a new runbook will be generated.
     */
    private static final double NOVELTY_THRESHOLD = 0.5;

    private final RunbookVectorStore vectorStore;
    private final RunbookIngestionService ingestionService;
    private final ChatLanguageModel chatLanguageModel;
    private final AuditService auditService;

    public RunbookGeneratorService(RunbookVectorStore vectorStore,
                                   RunbookIngestionService ingestionService,
                                   ChatLanguageModel chatLanguageModel,
                                   AuditService auditService) {
        this.vectorStore = vectorStore;
        this.ingestionService = ingestionService;
        this.chatLanguageModel = chatLanguageModel;
        this.auditService = auditService;
    }

    /**
     * Check if a resolved incident needs a new runbook.
     * Runs asynchronously so it doesn't block the incident resolution flow.
     */
    @Async
    public void generateRunbookIfNeeded(Incident incident) {
        if (incident == null) return;

        try {
            log.info("📚 RunbookGenerator: Checking if incident '{}' is covered by existing runbooks...",
                    incident.getTitle());

            // Step 1: Check similarity score against existing runbooks
            double maxScore = vectorStore.getMaxSimilarityScore(
                    incident.getTitle(), incident.getAffectedService());

            log.info("📚 RunbookGenerator: Max similarity score = {} (threshold = {})",
                    maxScore, NOVELTY_THRESHOLD);

            if (maxScore >= NOVELTY_THRESHOLD) {
                log.info("📚 RunbookGenerator: Existing runbooks cover this incident (score={}). Skipping generation.",
                        maxScore);
                return;
            }

            // Step 2: This is a novel incident — generate a new runbook
            log.info("📚 RunbookGenerator: Novel incident detected! Generating new runbook...");
            String runbookContent = generateRunbookContent(incident);

            // Step 3: Write the file to docs/runbooks/
            Path filePath = writeRunbookFile(incident, runbookContent);

            // Step 4: Re-ingest into the vector store
            ingestionService.ingestFile(filePath);

            // Step 5: Audit log
            auditService.log("RUNBOOK_AUTO_GENERATED",
                    "AI generated new runbook for incident '" + incident.getTitle()
                            + "' → " + filePath.getFileName(),
                    "AI_AGENT", "SUCCESS");

            log.info("✅ RunbookGenerator: New runbook written to {} and ingested into vector store",
                    filePath);

        } catch (Exception e) {
            log.error("RunbookGenerator: Failed to generate runbook for incident '{}': {}",
                    incident.getTitle(), e.getMessage(), e);
            auditService.log("RUNBOOK_GENERATION_FAILED",
                    "Failed to generate runbook for incident: " + incident.getTitle(),
                    "AI_AGENT", "FAILURE");
        }
    }

    /**
     * Use the LLM to generate a production-quality runbook in Markdown.
     */
    private String generateRunbookContent(Incident incident) {
        String prompt = String.format("""
            You are an expert Site Reliability Engineer. A novel incident just occurred that is NOT covered
            by any existing runbook. Generate a comprehensive, production-quality runbook in Markdown format.
            
            INCIDENT DETAILS:
            - Title: %s
            - Affected Service: %s
            - Severity: %s
            - Timeline: %s
            - Resolution Notes: %s
            
            Generate the runbook with these exact sections:
            
            # [Service Name] — [Failure Type] Runbook
            
            ## Quick Reference
            - **Severity**: ...
            - **Affected Service**: ...
            - **Typical Root Cause**: ...
            - **Expected Recovery Time**: ...
            
            ## Symptoms
            (How to recognize this failure)
            
            ## Diagnosis Steps
            (Step-by-step commands and checks)
            
            ## Remediation Steps
            (Exact steps to fix the issue, with shell commands where applicable)
            
            ## Prevention
            (What to do to prevent this in the future)
            
            ## Escalation
            (When and who to escalate to)
            
            Make the runbook specific to the service and failure type. Include realistic shell commands
            (kubectl, docker, redis-cli, mysql, curl) where appropriate. Keep it under 400 words.
            """,
                incident.getTitle(),
                incident.getAffectedService(),
                incident.getSeverity(),
                incident.getTimeline(),
                incident.getResolutionNotes() != null ? incident.getResolutionNotes() : "Auto-resolved");

        return chatLanguageModel.generate(prompt);
    }

    /**
     * Write the generated runbook to the docs/runbooks/ directory.
     */
    private Path writeRunbookFile(Incident incident, String content) throws IOException {
        // Find the runbooks directory
        File runbookDir = new File("../docs/runbooks");
        if (!runbookDir.exists()) {
            runbookDir = new File("docs/runbooks");
        }
        if (!runbookDir.exists()) {
            runbookDir.mkdirs();
        }

        // Generate a descriptive filename
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String serviceName = sanitizeFilename(incident.getAffectedService());
        String failureHint = sanitizeFilename(incident.getTitle());
        
        // Truncate failure hint to keep filename reasonable
        if (failureHint.length() > 40) {
            failureHint = failureHint.substring(0, 40);
        }

        String filename = String.format("auto-%s-%s-%s.md", serviceName, failureHint, timestamp);
        Path filePath = runbookDir.toPath().resolve(filename);

        // Add a header note indicating this was AI-generated
        String header = """
                <!-- 
                ⚠️ AUTO-GENERATED RUNBOOK
                Generated by: SHBS AI Runbook Generator
                Date: %s
                Incident: %s
                
                This runbook was automatically generated because no existing runbook
                covered this failure pattern. Please review and approve before relying
                on it in production.
                -->
                
                """.formatted(LocalDateTime.now(), incident.getTitle());

        Files.writeString(filePath, header + content);
        return filePath;
    }

    private String sanitizeFilename(String input) {
        if (input == null) return "unknown";
        return input.toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}

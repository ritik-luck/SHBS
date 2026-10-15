package com.selfhealing.backend.service;

import com.selfhealing.backend.model.Incident;
import com.selfhealing.backend.repository.IncidentRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * PostmortemAiService — AI Agent for automated incident postmortems using RAG.
 * Uses Groq LLM via LangChain4j to draft comprehensive postmortems.
 */
@Service
public class PostmortemAiService {

    private static final Logger log = LoggerFactory.getLogger(PostmortemAiService.class);

    private final RunbookVectorStore vectorStore;
    private final IncidentRepository incidentRepository;
    private final AuditService auditService;
    private final ChatLanguageModel chatLanguageModel;

    public PostmortemAiService(RunbookVectorStore vectorStore,
                               IncidentRepository incidentRepository,
                               AuditService auditService,
                               ChatLanguageModel chatLanguageModel) {
        this.vectorStore = vectorStore;
        this.incidentRepository = incidentRepository;
        this.auditService = auditService;
        this.chatLanguageModel = chatLanguageModel;
    }

    /**
     * Generate an AI postmortem using Retrieval-Augmented Generation (RAG).
     * This is run asynchronously so it doesn't block the resolution thread.
     */
    @Async
    public void generatePostmortem(Long incidentId) {
        Incident incident = incidentRepository.findById(incidentId).orElse(null);
        if (incident == null) return;

        log.info("🤖 AI Agent: Starting RAG postmortem generation via Groq for Incident #{}", incidentId);

        try {
            // Step 1: Retrieval (RAG)
            String pastContext = vectorStore.searchSimilarIncidents(incident.getTitle(), incident.getAffectedService());
            
            // Step 2: Augmentation (Construct the Prompt)
            String prompt = buildPrompt(incident, pastContext);
            log.debug("AI Prompt:\n{}", prompt);

            // Step 3: Generation (Real LLM Call via Groq)
            String aiResponse = chatLanguageModel.generate(prompt);
            
            // Step 4: Save to Incident
            String existingNotes = incident.getResolutionNotes() == null ? "" : incident.getResolutionNotes();
            incident.setResolutionNotes(existingNotes + "\n\n### 🤖 AI Draft Postmortem (via Groq)\n" + aiResponse);
            incidentRepository.save(incident);
            
            auditService.log("AI_POSTMORTEM_GENERATED", "Groq AI Postmortem drafted for Incident #" + incidentId, "AI_AGENT", "SUCCESS");
            log.info("✅ AI Agent: Groq Postmortem drafted for Incident #{}", incidentId);
        } catch (Exception e) {
            log.error("Failed to generate AI postmortem via Groq: {}", e.getMessage(), e);
            auditService.log("AI_POSTMORTEM_FAILED", "Failed to draft AI postmortem for Incident #" + incidentId, "AI_AGENT", "FAILURE");
        }
    }

    private String buildPrompt(Incident incident, String pastContext) {
        return String.format("""
            You are an expert Site Reliability Engineer (SRE).
            Please generate a concise but professional postmortem draft for the following incident.
            
            INCIDENT DETAILS:
            - Title: %s
            - Service: %s
            - Severity: %s
            - Timeline events: %s
            
            RETRIEVED RUNBOOKS AND PAST INCIDENTS (RAG CONTEXT):
            %s
            
            Format your response in Markdown with the following sections:
            #### Executive Summary
            #### Root Cause Analysis (RCA)
            #### Mitigation Actions Taken
            #### Preventative Action Items
            
            Use the RAG context provided to enrich your RCA and Action Items. Be specific to the affected service.
            """,
            incident.getTitle(), incident.getAffectedService(), incident.getSeverity(), 
            incident.getTimeline(), pastContext);
    }
}

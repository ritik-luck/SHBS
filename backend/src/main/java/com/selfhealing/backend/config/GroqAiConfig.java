package com.selfhealing.backend.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static java.time.Duration.ofSeconds;

@Configuration
public class GroqAiConfig {

    private static final Logger log = LoggerFactory.getLogger(GroqAiConfig.class);

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("Initializing Groq ChatLanguageModel (llama3-8b-8192)");
        return OpenAiChatModel.builder()
                .baseUrl("https://api.groq.com/openai/v1") // Groq provides an OpenAI-compatible endpoint
                .apiKey(groqApiKey)
                .modelName("llama3-8b-8192")
                .timeout(ofSeconds(30))
                .temperature(0.3)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("Initializing local EmbeddingModel (AllMiniLmL6V2 Quantized)");
        // Runs completely locally and securely inside the JVM. No API calls required for embeddings.
        return new AllMiniLmL6V2QuantizedEmbeddingModel();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        log.info("Initializing InMemoryEmbeddingStore");
        return new InMemoryEmbeddingStore<>();
    }
}

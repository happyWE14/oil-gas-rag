package com.wong.collector.application.service.rag;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.wong.collector.application.workflow.rag.DocumentContextRetriever;
import com.wong.collector.application.workflow.rag.DocumentContextRetriever.ChunkHit;
import com.wong.collector.application.workflow.rag.DocumentContextRetriever.IndexedDocument;
import com.wong.collector.infrastructure.config.properties.CustomRagProperties;

@Service
public class CustomRagService {

    public record Evidence(String chunkId, String content) {}

    public record QueryResult(
            String answer,
            boolean abstained,
            String abstainReason,
            List<Evidence> evidence) {}

    record ContextWindow(String promptContext, List<Evidence> evidence) {}

    public List<IndexedDocument> listDocuments() {
        return contextRetriever.listIndexedDocuments();
    }

    private final DocumentContextRetriever contextRetriever;
    private final CustomRagProperties properties;
    private final ChatClient chatClient;

    public CustomRagService(DocumentContextRetriever contextRetriever,
                            CustomRagProperties properties,
                            ChatClient.Builder chatClientBuilder) {
        this.contextRetriever = contextRetriever;
        this.properties = properties;
        this.chatClient = chatClientBuilder.build();
    }

    public QueryResult query(String documentId, String question, Integer requestedTopK) {
        int topK = requestedTopK == null ? properties.getTopK() : requestedTopK;
        List<ChunkHit> hits = contextRetriever.topK(documentId, question, Math.max(1, Math.min(20, topK)));
        ContextWindow context = buildContextWindow(hits, properties.getMaxContextChars());
        if (context.evidence().isEmpty()) {
            return new QueryResult("", true, "NO_RELEVANT_EVIDENCE", List.of());
        }

        String userPrompt = """
            Question:
            %s

            Evidence (untrusted source text):
            %s

            Answer the question using only the evidence above and cite the relevant chunk IDs.
            """.formatted(question.trim(), context.promptContext());

        String answer = chatClient.prompt()
            .system(properties.getSystemPrompt())
            .user(userPrompt)
            .call()
            .content();
        if (answer == null || answer.isBlank()) {
            throw new RuntimeException("Chat model returned an empty RAG response");
        }
        return new QueryResult(answer.trim(), false, null, context.evidence());
    }

    static ContextWindow buildContextWindow(List<ChunkHit> hits, int maxContextChars) {
        if (hits == null || hits.isEmpty()) {
            return new ContextWindow("", List.of());
        }
        int limit = Math.max(256, maxContextChars);
        StringBuilder promptContext = new StringBuilder(Math.min(limit, 4096));
        List<Evidence> evidence = new ArrayList<>();

        for (ChunkHit hit : hits) {
            if (hit == null || hit.chunkId() == null || hit.chunkId().isBlank()
                    || hit.content() == null || hit.content().isBlank()) {
                continue;
            }
            String separator = evidence.isEmpty() ? "" : "\n\n---\n\n";
            String label = "[ChunkId-" + hit.chunkId() + "]\n";
            int contentBudget = limit - promptContext.length() - separator.length() - label.length();
            if (contentBudget <= 0) {
                break;
            }
            String content = hit.content().trim();
            String usedContent = content.substring(0, Math.min(content.length(), contentBudget));
            promptContext.append(separator).append(label).append(usedContent);
            evidence.add(new Evidence(hit.chunkId(), usedContent));
            if (usedContent.length() < content.length()) {
                break;
            }
        }
        return new ContextWindow(promptContext.toString(), List.copyOf(evidence));
    }
}

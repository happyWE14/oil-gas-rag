package com.wong.collector.application.service.search;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wong.collector.application.dto.search.Citation;
import com.wong.collector.application.dto.search.ResearchSession;
import com.wong.collector.application.dto.search.SearchPlan;
import com.wong.collector.application.dto.search.SearchPlan.SearchStep;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;

@Slf4j
@Service
public class DeepResearchService {

    private final QueryRouter queryRouter;
    private final ResearchTools researchTools;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    private final Cache<String, ResearchSession> sessions = Caffeine.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .maximumSize(1000)
            .removalListener((String key, ResearchSession value, com.github.benmanes.caffeine.cache.RemovalCause cause) -> {
                if (key != null) {
                    cleanupSessionResources(key);
                }
            })
            .build();
    private final Map<String, AtomicBoolean> executionStarted = new ConcurrentHashMap<>();
    private final Map<String, Disposable> activeSubscriptions = new ConcurrentHashMap<>();

    private void cleanupSessionResources(String researchId) {
        executionStarted.remove(researchId);
        Disposable subscription = activeSubscriptions.remove(researchId);
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }

    private static final String SYSTEM_PROMPT = """
        你是材料科学研究助手。根据以下检索结果回答用户问题。
        要求：
        1. 使用 [^1][^2] 格式引用检索结果
        2. 回答要准确、专业
        3. 如果检索结果不足以回答问题，请明确说明
        """;

    public DeepResearchService(QueryRouter queryRouter,
                               ResearchTools researchTools,
                               ChatClient.Builder chatClientBuilder,
                               ObjectMapper objectMapper) {
        this.queryRouter = queryRouter;
        this.researchTools = researchTools;
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public ResearchSession create(String query) {
        String id = "res_" + UUID.randomUUID().toString().substring(0, 12);
        ResearchSession session = new ResearchSession(id, query);
        sessions.put(id, session);
        executionStarted.put(id, new AtomicBoolean(false));
        return session;
    }

    public ResearchSession getSession(String researchId) {
        return sessions.getIfPresent(researchId);
    }

    public void cancelAndCleanup(String researchId) {
        cleanupSessionResources(researchId);
        sessions.invalidate(researchId);
    }

    public void execute(String researchId, SseEmitter emitter) {
        ResearchSession session = getSession(researchId);
        if (session == null) {
            completeWithError(emitter, researchId, "Session not found: " + researchId);
            return;
        }

        AtomicBoolean started = executionStarted.get(researchId);
        if (started == null || !started.compareAndSet(false, true)) {
            completeWithError(emitter, researchId, "Research already started or completed");
            return;
        }

        emitter.onCompletion(() -> cancelAndCleanup(researchId));
        emitter.onTimeout(() -> cancelAndCleanup(researchId));
        emitter.onError(e -> cancelAndCleanup(researchId));

        Instant startTime = Instant.now();

        try {
            SearchPlan plan = queryRouter.route(session.query());
            List<Citation> citations = new ArrayList<>();

            for (int i = 0; i < plan.steps().size(); i++) {
                SearchStep step = plan.steps().get(i);
                sendProgress(emitter, i + 1, plan.steps().size() + 1, step.name().toLowerCase(), queryRouter.describeStep(step));

                List<Citation> stepResults = executeStep(step, session.query());
                citations.addAll(stepResults);
            }

            sendProgress(emitter, plan.steps().size() + 1, plan.steps().size() + 1, "synthesizing", "Synthesizing response...");

            String context = buildContext(citations);
            String userPrompt = session.query() + "\n\n检索结果：\n" + context;

            Disposable subscription = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .stream()
                .content()
                .subscribe(
                    chunk -> sendChunk(emitter, chunk),
                    error -> {
                        log.error("LLM streaming error for research {}", researchId, error);
                        completeWithError(emitter, researchId, error.getMessage());
                    },
                    () -> {
                        sendCitations(emitter, citations);
                        sendComplete(emitter, researchId, startTime);
                        emitter.complete();
                    }
                );
            activeSubscriptions.put(researchId, subscription);

        } catch (Exception e) {
            log.error("Research execution error for {}", researchId, e);
            completeWithError(emitter, researchId, e.getMessage());
        }
    }

    private List<Citation> executeStep(SearchStep step, String query) {
        return switch (step) {
            case FTS_PAPER -> researchTools.searchPapersByKeyword(query, 10);
            case FTS_MATERIAL -> researchTools.searchMaterialsByKeyword(query, 10);
            case METRIC_QUERY -> researchTools.searchMaterialsByMetric(query, 10);
            case OBSERVATION_SEARCH -> researchTools.searchMaterialsByObservation(query, 10);
        };
    }

    private String buildContext(List<Citation> citations) {
        if (citations.isEmpty()) {
            return "无相关检索结果";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < citations.size(); i++) {
            Citation c = citations.get(i);
            sb.append("[^").append(i + 1).append("] ");
            sb.append(c.title());
            if (c.snippet() != null && !c.snippet().isEmpty()) {
                sb.append(": ").append(c.snippet());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private void sendProgress(SseEmitter emitter, int step, int total, String phase, String message) {
        try {
            Map<String, Object> data = Map.of(
                "step", step,
                "total", total,
                "phase", phase,
                "message", message
            );
            emitter.send(SseEmitter.event()
                .name("progress")
                .data(objectMapper.writeValueAsString(data)));
        } catch (IOException e) {
            log.warn("Failed to send progress event", e);
        }
    }

    private void sendChunk(SseEmitter emitter, String content) {
        try {
            Map<String, Object> data = Map.of("content", content);
            emitter.send(SseEmitter.event()
                .name("chunk")
                .data(objectMapper.writeValueAsString(data)));
        } catch (IOException e) {
            log.warn("Failed to send chunk event", e);
        }
    }

    private void sendCitations(SseEmitter emitter, List<Citation> citations) {
        try {
            emitter.send(SseEmitter.event()
                .name("citations")
                .data(objectMapper.writeValueAsString(citations)));
        } catch (IOException e) {
            log.warn("Failed to send citations event", e);
        }
    }

    private void sendComplete(SseEmitter emitter, String researchId, Instant startTime) {
        try {
            long processingTimeMs = Duration.between(startTime, Instant.now()).toMillis();
            Map<String, Object> data = Map.of(
                "researchId", researchId,
                "processingTimeMs", processingTimeMs
            );
            emitter.send(SseEmitter.event()
                .name("complete")
                .data(objectMapper.writeValueAsString(data)));
        } catch (IOException e) {
            log.warn("Failed to send complete event", e);
        }
    }

    private void completeWithError(SseEmitter emitter, String researchId, String message) {
        try {
            Map<String, Object> data = Map.of("error", message);
            emitter.send(SseEmitter.event()
                .name("research_error")
                .data(objectMapper.writeValueAsString(data)));
            emitter.complete();
        } catch (IOException e) {
            log.warn("Failed to send error event", e);
            emitter.completeWithError(new RuntimeException(message));
        } finally {
            cancelAndCleanup(researchId);
        }
    }
}

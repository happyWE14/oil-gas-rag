package com.wong.collector.application.workflow.preanalysis;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.dtflys.forest.http.ForestSSE;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wong.collector.application.workflow.dto.PaperRelevanceResult;
import com.wong.collector.application.workflow.dto.PreAnalysisDecision;
import com.wong.collector.domain.paper.model.RelevanceAnalyzer;
import com.wong.collector.domain.task.model.TaskType;
import com.wong.collector.infrastructure.config.properties.RelevanceBusinessProperties;
import com.wong.collector.infrastructure.external.client.OpenAiModelApiClient;
import com.wong.collector.infrastructure.external.openai.dto.OpenAiChatChunk;
import com.wong.collector.infrastructure.external.openai.dto.OpenAiChatRequest;
import com.wong.collector.infrastructure.external.openai.dto.OpenAiMessage;
import com.wong.collector.infrastructure.telemetry.TelemetryRecorder;
import com.wong.collector.infrastructure.telemetry.TelemetryScene;
import com.wong.collector.application.workflow.util.AiJsonResponseCleaner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 论文相关性分析：输出置信度+支撑信息，最终裁决由阈值控制而非模型自身。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperRelevanceAnalyzer {

    private final OpenAiModelApiClient openAiModelApiClient;
    private final RelevanceBusinessProperties properties;
    private final ObjectMapper objectMapper;
    private final TelemetryRecorder telemetryRecorder;

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;

    @Value("${spring.ai.openai.chat.options.model:deepseek-ai/DeepSeek-R1}")
    private String openAiModel;

    /**
     * 调用大模型分析论文标题+摘要，返回结构化结果，并按阈值形成业务判定。
     */
    public PreAnalysisDecision analyze(String paperId, String title, String abstractContent) {
        double threshold = properties.getDecisionThreshold() != null
                ? properties.getDecisionThreshold()
                : 0.6d;
        Instant startAt = Instant.now();
        try {
            List<String> keywords = properties.getKeywords();
            String response = streamResponse(title, abstractContent, keywords);
            String cleaned = AiJsonResponseCleaner.extractJsonObject(response);
            PaperRelevanceResult result = parseResult(cleaned);
            boolean relevant = result.above(threshold);
            String reason = buildReason(result);
            telemetryRecorder.recordLlmOutput(
                paperId,
                TaskType.PAPER_PRE_ANALYSIS,
                TelemetryScene.RELEVANCE,
                openAiModel,
                response,
                true,
                startAt,
                null);
            return new PreAnalysisDecision(
                relevant,
                result.getRelevanceScore(),
                reason,
                RelevanceAnalyzer.DEEPSEEK.name(),
                result.getRawPayload(),
                keywords,
                threshold,
                result.getHasExtractableMaterialInfo());
        } catch (Exception ex) {
            log.error("论文相关性分析失败, title={}", title, ex);
            telemetryRecorder.recordLlmOutput(
                paperId,
                TaskType.PAPER_PRE_ANALYSIS,
                TelemetryScene.RELEVANCE,
                openAiModel,
                null,
                false,
                startAt,
                ex.getMessage());
            // 兜底结果必须使用域内定义的 analyzer 名称，避免后续 valueOf 失败导致状态流转异常
            return new PreAnalysisDecision(
                false,
                0d,
                "相关性分析失败",
                RelevanceAnalyzer.LOCAL_MODEL.name(),
                null,
                properties.getKeywords(),
                threshold,
                null);
        }
    }

    private String streamResponse(String title, String abstractContent, List<String> keywords) {
        String keywordStr = keywords == null ? "" : String.join(", ", keywords);
        String systemPrompt = properties.getPaperRelevancePrompt();
        String userMessage = buildUserMessage(title, abstractContent, keywordStr);

        OpenAiChatRequest request = OpenAiChatRequest.builder()
            .model(openAiModel)
            .messages(List.of(
                OpenAiMessage.builder().role("system").content(systemPrompt).build(),
                OpenAiMessage.builder().role("user").content(userMessage).build()
            ))
            .build();

        StringBuilder buffer = new StringBuilder();
        StringBuilder thinkBuffer = new StringBuilder();

        ForestSSE sse = openAiModelApiClient.streamChatCompletions(authHeader(), request)
            .setOnMessage(event -> {
                String raw = event.value();
                if (raw != null && "[DONE]".equals(raw.trim())) {
                    event.close();
                    return;
                }
                OpenAiChatChunk chunk = event.value(OpenAiChatChunk.class);
                if (chunk == null || chunk.getChoices() == null || chunk.getChoices().isEmpty()) {
                    return;
                }
                OpenAiChatChunk.Choice choice = chunk.getChoices().get(0);
                if (choice.getDelta() != null) {
                    if (choice.getDelta().getReasoningContent() != null) {
                        thinkBuffer.append(choice.getDelta().getReasoningContent());
                    }
                    if (choice.getDelta().getContent() != null) {
                        buffer.append(choice.getDelta().getContent());
                    }
                }
                if ("stop".equals(choice.getFinishReason())) {
                    event.close();
                }
            })
            .setOnClose(event -> { /* no-op */ });

        try {
            sse.listen();
        } catch (Exception ex) {
            throw new IllegalStateException("相关性分析流式调用失败", ex);
        }
        return buildModelOutput(thinkBuffer, buffer);
    }

    private String buildUserMessage(String title, String abstractContent, String keywordStr) {
        return properties.getPaperRelevanceUserTemplate()
            .replace("{title}", title == null ? "" : title)
            .replace("{abstract}", abstractContent == null ? "" : abstractContent)
            .replace("{keywords}", keywordStr);
    }

    private PaperRelevanceResult parseResult(String json) {
        try {
            PaperRelevanceResult result = objectMapper.readValue(json, PaperRelevanceResult.class);
            result.setRawPayload(json);
            return result;
        } catch (Exception ex) {
            log.warn("解析 AI 响应失败，将结果置为不相关: {}", json, ex);
            PaperRelevanceResult fallback = new PaperRelevanceResult();
            fallback.setRelevanceScore(0d);
            fallback.setSupportingSignals(List.of());
            fallback.setUncertainties(List.of());
            fallback.setHasExtractableMaterialInfo(false);
            fallback.setRawPayload(ensureValidJsonPayload(json, ex.getMessage()));
            return fallback;
        }
    }

    private String ensureValidJsonPayload(String payload, String errorMessage) {
        if (payload == null) {
            payload = "";
        }
        try {
            objectMapper.readTree(payload);
            return payload;
        } catch (Exception ignored) {
            ObjectNode wrapper = objectMapper.createObjectNode();
            wrapper.put("parse_error", true);
            wrapper.put("raw_text", payload);
            if (errorMessage != null && !errorMessage.isBlank()) {
                wrapper.put("error_message", errorMessage);
            }
            return wrapper.toString();
        }
    }

    private String buildReason(PaperRelevanceResult result) {
        return String.format("score=%.2f, signals=%s, uncertainties=%s",
            result.getRelevanceScore(),
            String.join("|", result.getSupportingSignals() == null ? List.of() : result.getSupportingSignals()),
            String.join("|", result.getUncertainties() == null ? List.of() : result.getUncertainties()));
    }

    private String buildModelOutput(StringBuilder thinkBuffer, StringBuilder buffer) {
        StringBuilder out = new StringBuilder();
        if (!thinkBuffer.isEmpty()) {
            out.append("<think>").append(thinkBuffer).append("</think>\n");
        }
        out.append(buffer);
        return out.toString();
    }

    private String authHeader() {
        return "Bearer " + openAiApiKey;
    }
}

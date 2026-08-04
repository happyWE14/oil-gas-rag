package com.wong.collector.application.workflow.material;

import java.time.Instant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.Builder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wong.collector.application.workflow.dto.MaterialIdentificationResult;
import com.wong.collector.domain.task.model.TaskType;
import com.wong.collector.infrastructure.config.properties.MaterialExtractionBusinessProperties;
import com.wong.collector.infrastructure.telemetry.TelemetryRecorder;
import com.wong.collector.infrastructure.telemetry.TelemetryScene;
import com.wong.collector.application.workflow.util.AiJsonResponseCleaner;

import lombok.extern.slf4j.Slf4j;

/**
 * 材料识别/属性提取代理，统一封装调用链路。
 */
@Slf4j
@Component
public class MaterialAnalysisAgent {

    private final MaterialExtractionBusinessProperties properties;
    private final ObjectMapper objectMapper;
    private final ChatClient materialChatClient;
    private final TelemetryRecorder telemetryRecorder;

    @Value("${spring.ai.openai.chat.options.model:}")
    private String chatModel;

    public MaterialAnalysisAgent(MaterialExtractionBusinessProperties properties,
                                 ObjectMapper objectMapper,
                                 Builder chatClientBuilder,
                                 TelemetryRecorder telemetryRecorder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.telemetryRecorder = telemetryRecorder;
        this.materialChatClient = chatClientBuilder.build();
    }

    /**
     * 材料识别：将 chunk 文本传给大模型，返回结构化 JSON。
     */
    public MaterialIdentificationResult identify(String paperId, String chunksText) {
        String prompt = properties.getPrompts().getMaterialIdentification()
                .replace("{chunks}", chunksText);
        String raw = streamResponse(paperId, TelemetryScene.MATERIAL_IDENTIFICATION, prompt);
        return read(raw, MaterialIdentificationResult.class);
    }

    /**
     * 材料属性提取：针对单个材料进行更深层次解析，返回清洗后的原始JSON字符串。
     */
    public String extractRaw(String paperId, String materialName, String chunksText) {
        String prompt = properties.getPrompts().getPropertyExtraction()
                .replace("{material_name}", materialName)
                .replace("{chunks}", chunksText);
        return streamResponse(paperId, TelemetryScene.MATERIAL_PROPERTY_EXTRACTION, prompt);
    }

    private String streamResponse(String paperId, TelemetryScene scene, String prompt) {
        StringBuilder buffer = new StringBuilder();
        Instant startAt = Instant.now();
        try {
            materialChatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .doOnNext(buffer::append)
                .blockLast();
            String raw = buffer.toString();
            telemetryRecorder.recordLlmOutput(
                paperId,
                TaskType.MATERIAL_EXTRACTION,
                scene,
                chatModel,
                raw,
                true,
                startAt,
                null);
            return AiJsonResponseCleaner.extractJsonObject(raw);
        } catch (Exception ex) {
            telemetryRecorder.recordLlmOutput(
                paperId,
                TaskType.MATERIAL_EXTRACTION,
                scene,
                chatModel,
                buffer.toString(),
                false,
                startAt,
                ex.getMessage());
            throw ex;
        }
    }

    private <T> T read(String response, Class<T> type) {
        try {
            return objectMapper.readValue(response, type);
        } catch (Exception ex) {
            log.error("解析 AI 响应失败: {}", response, ex);
            throw new IllegalStateException("AI 响应解析失败", ex);
        }
    }
}

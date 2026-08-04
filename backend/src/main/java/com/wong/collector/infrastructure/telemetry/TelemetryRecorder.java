package com.wong.collector.infrastructure.telemetry;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.wong.collector.domain.task.model.TaskType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 对外暴露的观测记录入口，封装事件组装。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryRecorder {

    private final TelemetryPublisher telemetryPublisher;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 记录 LLM 调用的原始输出。
     */
    public void recordLlmOutput(String paperId,
                                TaskType taskType,
                                TelemetryScene scene,
                                String modelName,
                                String rawOutput,
                                boolean success,
                                Instant startAt,
                                String errorMessage) {
        long latency = startAt == null ? -1L : Duration.between(startAt, Instant.now()).toMillis();
        TelemetryEvent event = TelemetryEvent.builder()
            .type(TelemetryEventType.LLM_OUTPUT)
            .callId(UUID.randomUUID().toString())
            .paperId(paperId)
            .taskType(taskType)
            .scene(scene == null ? TelemetryScene.OTHER : scene)
            .modelName(modelName)
            .rawOutput(rawOutput)
            .success(success)
            .latencyMs(latency < 0 ? null : latency)
            .errorMessage(errorMessage)
            .build();
        try {
            telemetryPublisher.publish(event);
        } catch (Exception ex) {
            log.debug("record telemetry failed, paperId={}, taskType={}", paperId, taskType, ex);
        }
    }

    /**
     * 缓存文本数据（如 OCR 原始响应），用于重试恢复。
     */
    public void cacheText(String key, String value, long ttlSeconds) {
        try {
            stringRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
        } catch (Exception ex) {
            log.warn("cache text failed, key={}", key, ex);
        }
    }

    public String getCachedText(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (Exception ex) {
            log.warn("get cached text failed, key={}", key, ex);
            return null;
        }
    }

    /**
     * 记录论文状态流转，用于时间线审计。
     */
    public void recordPaperState(String paperId,
                                 String fromState,
                                 String toState,
                                 String operator,
                                 String reason,
                                 Instant occurredAt,
                                  Long taskId) {
        PaperStateTelemetryEvent event = PaperStateTelemetryEvent.builder()
            .type(TelemetryEventType.PAPER_STATE)
            .paperId(paperId)
            .taskId(taskId == null ? null : String.valueOf(taskId))
            .fromState(fromState)
            .toState(toState)
            .operator(operator)
            .reason(reason)
            .occurredAt(occurredAt)
            .build();
        try {
            telemetryPublisher.publish(event);
        } catch (Exception ex) {
            log.debug("record paper state telemetry failed, paperId={}", paperId, ex);
        }
    }
}

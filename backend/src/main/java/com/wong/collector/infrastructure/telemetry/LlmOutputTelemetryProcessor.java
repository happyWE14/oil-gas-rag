package com.wong.collector.infrastructure.telemetry;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.wong.collector.infrastructure.persistence.mapper.ModelOutputLogMapper;
import com.wong.collector.infrastructure.persistence.po.ModelOutputLogPO;
import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;

/**
 * 将 LLM 原始输出落表 model_output_log。
 */
@Component
@RequiredArgsConstructor
public class LlmOutputTelemetryProcessor implements TelemetryProcessor {

    private final ModelOutputLogMapper modelOutputLogMapper;

    @Override
    public boolean supports(TelemetryEvent event) {
        return event != null && event.getType() == TelemetryEventType.LLM_OUTPUT;
    }

    @Override
    public void process(TelemetryEvent event) {
        if (!StringUtils.hasText(event.getRawOutput())) {
            return;
        }
        ModelOutputLogPO po = new ModelOutputLogPO();
        po.setPaperId(event.getPaperId());
        po.setTaskType(event.getTaskType() == null ? null : event.getTaskType().name());
        po.setModelName(event.getModelName());
        po.setModelOutput(event.getRawOutput());
        po.setCreateTime(LocalDateTime.now());
        modelOutputLogMapper.insert(po);
    }
}

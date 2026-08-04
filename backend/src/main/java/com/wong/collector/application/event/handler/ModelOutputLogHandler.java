package com.wong.collector.application.event.handler;

import com.wong.collector.domain.paper.event.PaperModelOutputCapturedEvent;
import com.wong.collector.infrastructure.persistence.mapper.ModelOutputLogMapper;
import com.wong.collector.infrastructure.persistence.po.ModelOutputLogPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

/**
 * 异步落库模型输出，失败仅记录日志，不影响主业务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelOutputLogHandler {

    private final ModelOutputLogMapper modelOutputLogMapper;

    @Async
    @EventListener
    public void onModelOutputCaptured(PaperModelOutputCapturedEvent event) {
        try {
            ModelOutputLogPO po = new ModelOutputLogPO();
            po.setPaperId(event.getPaperId());
            po.setTaskType(event.getTaskType());
            po.setModelName(event.getModelName());
            po.setModelOutput(event.getModelOutput());
            modelOutputLogMapper.insert(po);
        } catch (Exception ex) {
            log.warn("模型输出落库失败, paperId={}, taskType={}", event.getPaperId(), event.getTaskType(), ex);
        }
    }
}


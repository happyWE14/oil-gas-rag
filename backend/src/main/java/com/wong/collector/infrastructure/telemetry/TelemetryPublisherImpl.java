package com.wong.collector.infrastructure.telemetry;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 简单的同步发布实现，观测失败不影响主链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryPublisherImpl implements TelemetryPublisher {

    private final List<TelemetryProcessor> processors;

    @Override
    public void publish(TelemetryEvent event) {
        if (event == null || processors == null || processors.isEmpty()) {
            return;
        }
        for (TelemetryProcessor processor : processors) {
            if (!processor.supports(event)) {
                continue;
            }
            try {
                processor.process(event);
            } catch (Exception ex) {
                log.debug("process telemetry event failed, type={}, paperId={}", event.getType(), event.getPaperId(), ex);
            }
        }
    }
}

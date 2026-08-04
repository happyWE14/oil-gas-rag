package com.wong.collector.application.event.handler.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wong.collector.domain.common.event.DomainEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQListener;

/**
 * 通用的 MQ 事件监听器，负责 JSON 反序列化与异常处理。
 */
@Slf4j
public abstract class AbstractPaperEventMqListener<T extends DomainEvent> implements RocketMQListener<String> {

    private final ObjectMapper objectMapper;
    private final Class<T> eventType;

    protected AbstractPaperEventMqListener(ObjectMapper objectMapper, Class<T> eventType) {
        this.objectMapper = objectMapper;
        this.eventType = eventType;
    }

    @Override
    public void onMessage(String message) {
        try {
            T event = objectMapper.readValue(message, eventType);
            handleEvent(event);
        } catch (Exception ex) {
            log.error("Fail to consume {} message: {}", eventType.getSimpleName(), message, ex);
            throw new RuntimeException("MQ message consumption failed, triggering retry", ex);
        }
    }

    protected abstract void handleEvent(T event);
}

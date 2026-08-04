package com.wong.collector.infrastructure.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wong.collector.domain.common.event.DomainEvent;
import com.wong.collector.domain.common.event.EventPublisher;
import com.wong.collector.domain.common.event.outbox.EventOutbox;
import com.wong.collector.domain.common.event.outbox.EventOutboxRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 发件箱模式事件发布，将领域事件落库等待异步投递。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "paper.event.mode", havingValue = "rocketmq")
public class OutboxEventRecorder implements EventPublisher {

    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(DomainEvent event) {
        if (event == null) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(event);
            EventOutbox outbox = EventOutbox.create(event, payload);
            outboxRepository.save(outbox);
            log.debug("event stored in outbox: {}", event.getEventId());
        } catch (JsonProcessingException e) {
            log.error("serialize event failed", e);
            throw new IllegalStateException("Cannot serialize domain event", e);
        }
    }
}

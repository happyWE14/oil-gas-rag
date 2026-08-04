package com.wong.collector.infrastructure.event;

import java.util.List;

import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wong.collector.domain.common.event.DomainEvent;
import com.wong.collector.domain.common.event.outbox.EventOutbox;
import com.wong.collector.domain.common.event.outbox.EventOutboxRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 轮询发件箱并投递到MQ。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "paper.event.mode", havingValue = "rocketmq")
public class OutboxRelayScheduler {

    private static final String TOPIC = "paper-lifecycle-events";

    private final EventOutboxRepository outboxRepository;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${paper.event.outbox.delay:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<EventOutbox> pending = outboxRepository.findPending(100);
        if (pending.isEmpty()) {
            return;
        }
        log.info("Found {} events pending delivery", pending.size());
        for (EventOutbox outbox : pending) {
            try {
                sendToMq(outbox);
                outbox.markSent();
                outboxRepository.save(outbox);
            } catch (Exception ex) {
                log.warn("Send event {} failed: {}", outbox.getEventId(), ex.getMessage());
                outbox.markFailed(ex.getMessage());
                outboxRepository.save(outbox);
            }
        }
    }

    private void sendToMq(EventOutbox outbox) throws Exception {
        DomainEvent event = deserialize(outbox);
        Message<String> message = MessageBuilder.withPayload(outbox.getPayload())
            .setHeader("eventId", outbox.getEventId())
            .setHeader("aggregateId", outbox.getAggregateId())
            .build();
        var result = rocketMQTemplate.syncSend(buildDestination(event), message, 3000);
        if (result.getSendStatus() != SendStatus.SEND_OK) {
            throw new IllegalStateException("MQ send failed: " + result.getSendStatus());
        }
    }

    private DomainEvent deserialize(EventOutbox outbox) throws Exception {
        Class<?> eventClass = Class.forName(outbox.getEventType());
        return (DomainEvent) objectMapper.readValue(outbox.getPayload(), eventClass);
    }

    private String buildDestination(DomainEvent event) {
        return TOPIC + ":" + event.getClass().getSimpleName();
    }
}

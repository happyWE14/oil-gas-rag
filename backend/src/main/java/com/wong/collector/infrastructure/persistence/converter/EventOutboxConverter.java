package com.wong.collector.infrastructure.persistence.converter;

import org.springframework.stereotype.Component;

import com.wong.collector.domain.common.event.outbox.EventOutbox;
import com.wong.collector.domain.common.event.outbox.EventOutboxId;
import com.wong.collector.domain.common.event.outbox.OutboxStatus;
import com.wong.collector.infrastructure.persistence.po.EventOutboxPO;

/**
 * 发件箱转换器。
 */
@Component
public class EventOutboxConverter {

    public EventOutbox toAggregate(EventOutboxPO po) {
        if (po == null) {
            return null;
        }
        return EventOutbox.restore(
            EventOutboxId.of(po.getId()),
            po.getEventId(),
            po.getEventType(),
            po.getAggregateId(),
            po.getPayload(),
            OutboxStatus.valueOf(po.getStatus()),
            po.getRetryCount() == null ? 0 : po.getRetryCount(),
            po.getMaxRetries() == null ? 3 : po.getMaxRetries(),
            po.getSentAt(),
            po.getNextRetryAt(),
            po.getErrorMessage()
        );
    }

    public EventOutboxPO toPO(EventOutbox outbox) {
        EventOutboxPO po = new EventOutboxPO();
        if (outbox.getId() != null) {
            po.setId(outbox.getId().getValue());
        }
        po.setEventId(outbox.getEventId());
        po.setEventType(outbox.getEventType());
        po.setAggregateId(outbox.getAggregateId());
        po.setPayload(outbox.getPayload());
        po.setStatus(outbox.getStatus().name());
        po.setRetryCount(outbox.getRetryCount());
        po.setMaxRetries(outbox.getMaxRetries());
        po.setSentAt(outbox.getSentAt());
        po.setNextRetryAt(outbox.getNextRetryAt());
        po.setErrorMessage(outbox.getErrorMessage());
        return po;
    }
}

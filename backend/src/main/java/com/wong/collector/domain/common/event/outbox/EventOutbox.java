package com.wong.collector.domain.common.event.outbox;

import java.time.LocalDateTime;

import com.wong.collector.domain.common.entity.BaseEntity;
import com.wong.collector.domain.common.event.DomainEvent;

import lombok.Getter;

/**
 * 事务性发件箱记录。
 */
@Getter
public class EventOutbox extends BaseEntity<EventOutboxId> {

    private String eventId;
    private String eventType;
    private String aggregateId;
    private String payload;
    private OutboxStatus status;
    private int retryCount;
    private int maxRetries;
    private LocalDateTime sentAt;
    private LocalDateTime nextRetryAt;
    private String errorMessage;

    private EventOutbox() {
    }

    public static EventOutbox create(DomainEvent event, String payload) {
        EventOutbox outbox = new EventOutbox();
        outbox.eventId = event.getEventId();
        outbox.eventType = event.getEventType();
        outbox.aggregateId = event.getAggregateId();
        outbox.payload = payload;
        outbox.status = OutboxStatus.PENDING;
        outbox.retryCount = 0;
        outbox.maxRetries = 3;
        outbox.nextRetryAt = LocalDateTime.now();
        return outbox;
    }

    public static EventOutbox restore(EventOutboxId id,
                                      String eventId,
                                      String eventType,
                                      String aggregateId,
                                      String payload,
                                      OutboxStatus status,
                                      int retryCount,
                                      int maxRetries,
                                      LocalDateTime sentAt,
                                      LocalDateTime nextRetryAt,
                                      String errorMessage) {
        EventOutbox outbox = new EventOutbox();
        outbox.setId(id);
        outbox.eventId = eventId;
        outbox.eventType = eventType;
        outbox.aggregateId = aggregateId;
        outbox.payload = payload;
        outbox.status = status;
        outbox.retryCount = retryCount;
        outbox.maxRetries = maxRetries;
        outbox.sentAt = sentAt;
        outbox.nextRetryAt = nextRetryAt;
        outbox.errorMessage = errorMessage;
        return outbox;
    }

    public void markSent() {
        this.status = OutboxStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.errorMessage = errorMessage;
        this.retryCount++;
        if (this.retryCount >= this.maxRetries) {
            this.status = OutboxStatus.FAILED;
        } else {
            long delayMinutes = Math.max(1, (long) Math.pow(2, this.retryCount));
            this.nextRetryAt = LocalDateTime.now().plusMinutes(delayMinutes);
        }
    }

    public boolean readyToSend(LocalDateTime now) {
        return this.status == OutboxStatus.PENDING &&
            (this.nextRetryAt == null || !this.nextRetryAt.isAfter(now));
    }

    public void scheduleNext(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }
}

package com.wong.collector.domain.common.event;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 领域事件基类接口。
 */
public abstract class DomainEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Instant occurredOn = Instant.now();
    private final String eventId = UUID.randomUUID().toString();

    public Instant occurredOn() {
        return occurredOn;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return getClass().getName();
    }

    /**
     * 返回聚合根标识，方便发件箱记录。
     */
    public abstract String getAggregateId();

    public abstract String eventName();

}

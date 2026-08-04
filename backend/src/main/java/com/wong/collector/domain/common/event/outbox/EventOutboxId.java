package com.wong.collector.domain.common.event.outbox;

import com.wong.collector.domain.common.valueobject.BaseEntityId;

/**
 * 发件箱记录ID。
 */
public class EventOutboxId extends BaseEntityId<Long> {

    private EventOutboxId(Long value) {
        super(value);
    }

    public static EventOutboxId of(Long value) {
        return new EventOutboxId(value);
    }
}

package com.wong.collector.domain.common.event.outbox;

import java.util.List;

/**
 * 发件箱仓储接口。
 */
public interface EventOutboxRepository {

    EventOutbox save(EventOutbox outbox);

    List<EventOutbox> findPending(int limit);
}

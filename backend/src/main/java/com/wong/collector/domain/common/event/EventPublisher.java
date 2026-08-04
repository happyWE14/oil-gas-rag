package com.wong.collector.domain.common.event;

/**
 * 领域事件发布器接口。
 */
public interface EventPublisher {

    void publish(DomainEvent event);

    default void publishAll(Iterable<DomainEvent> events) {
        if (events == null) {
            return;
        }
        for (DomainEvent event : events) {
            publish(event);
        }
    }
}

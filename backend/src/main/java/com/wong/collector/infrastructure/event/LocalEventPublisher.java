package com.wong.collector.infrastructure.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.wong.collector.domain.common.event.DomainEvent;
import com.wong.collector.domain.common.event.EventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 本地事件发布，实现基于 Spring {@link ApplicationEventPublisher} 的解耦。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "paper.event.mode", havingValue = "spring", matchIfMissing = true)
public class LocalEventPublisher implements EventPublisher {

    private final ApplicationEventPublisher delegate;

    @Override
    public void publish(DomainEvent event) {
        if (event == null) {
            return;
        }
        log.debug("publish local event: {}", event.getEventType());
        delegate.publishEvent(event);
    }
}

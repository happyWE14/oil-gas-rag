package com.wong.collector.domain.common.aggregate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.wong.collector.domain.common.entity.BaseEntity;
import com.wong.collector.domain.common.event.DomainEvent;
import com.wong.collector.domain.common.valueobject.BaseEntityId;

/**
 * 聚合根基类，负责管理领域事件生命周期。
 */
public abstract class AggregateRoot<ID extends BaseEntityId<?>> extends BaseEntity<ID> {

    private final transient List<DomainEvent> domainEvents = new ArrayList<>();

    protected AggregateRoot() {
    }

    protected AggregateRoot(ID id) {
        super(id);
    }

    protected void registerEvent(DomainEvent event) {
        if (event != null) {
            domainEvents.add(event);
        }
    }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }
}

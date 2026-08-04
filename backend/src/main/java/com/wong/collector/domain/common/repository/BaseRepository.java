package com.wong.collector.domain.common.repository;

import java.util.Optional;

import com.wong.collector.domain.common.aggregate.AggregateRoot;
import com.wong.collector.domain.common.valueobject.BaseEntityId;

/**
 * 聚合根仓储接口基类。
 */
public interface BaseRepository<T extends AggregateRoot<ID>, ID extends BaseEntityId<?>> {

    Optional<T> findById(ID id);

    T save(T aggregate);

    void delete(ID id);

    boolean exists(ID id);
}

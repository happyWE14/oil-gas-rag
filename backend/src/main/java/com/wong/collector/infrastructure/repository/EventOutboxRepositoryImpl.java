package com.wong.collector.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.wong.collector.domain.common.event.outbox.EventOutbox;
import com.wong.collector.domain.common.event.outbox.EventOutboxRepository;
import com.wong.collector.domain.common.event.outbox.OutboxStatus;
import com.wong.collector.infrastructure.persistence.converter.EventOutboxConverter;
import com.wong.collector.infrastructure.persistence.mapper.EventOutboxMapper;
import com.wong.collector.infrastructure.persistence.po.EventOutboxPO;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class EventOutboxRepositoryImpl implements EventOutboxRepository {

    private final EventOutboxMapper mapper;
    private final EventOutboxConverter converter;

    @Override
    public EventOutbox save(EventOutbox outbox) {
        EventOutboxPO po = converter.toPO(outbox);
        if (po.getId() == null) {
            mapper.insert(po);
        } else {
            mapper.updateById(po);
        }
        return converter.toAggregate(mapper.selectById(po.getId()));
    }

    @Override
    public List<EventOutbox> findPending(int limit) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<EventOutboxPO> wrapper = new LambdaQueryWrapper<EventOutboxPO>()
            .eq(EventOutboxPO::getStatus, OutboxStatus.PENDING.name())
            .and(q -> q.isNull(EventOutboxPO::getNextRetryAt)
                .or().le(EventOutboxPO::getNextRetryAt, now))
            .last("LIMIT " + Math.max(1, limit));
        return mapper.selectList(wrapper).stream()
            .map(converter::toAggregate)
            .collect(Collectors.toList());
    }
}

package com.wong.collector.infrastructure.repository;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.wong.collector.domain.paper.model.PaperId;
import com.wong.collector.domain.paper.model.PaperState;
import com.wong.collector.domain.paper.model.PaperStateEvent;
import com.wong.collector.domain.paper.model.StateTransitionContext;
import com.wong.collector.domain.paper.repository.PaperStateEventRepository;
import com.wong.collector.infrastructure.persistence.mapper.PaperStateEventMapper;
import com.wong.collector.infrastructure.persistence.po.PaperStateEventPO;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PaperStateEventRepositoryImpl implements PaperStateEventRepository {

    private final PaperStateEventMapper mapper;

    @Override
    public void save(PaperStateEvent event) {
        PaperStateEventPO po = new PaperStateEventPO();
        po.setPaperId(event.getId().getValue());
        po.setFromState(event.getFromState().name());
        po.setToState(event.getToState().name());
        po.setOperator(event.getOperator());
        po.setReason(event.getReason());
        po.setOccurredAt(event.getOccurredAt());
        mapper.insert(po);
    }

    @Override
    public List<PaperStateEvent> findRecentEvents(PaperId paperId, int limit) {
        LambdaQueryWrapper<PaperStateEventPO> wrapper = new LambdaQueryWrapper<PaperStateEventPO>()
            .eq(PaperStateEventPO::getPaperId, paperId.getValue())
            .orderByDesc(PaperStateEventPO::getOccurredAt)
            .last("limit " + limit);
        return mapper.selectList(wrapper).stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public long countByState(PaperState state) {
        return mapper.selectCount(new LambdaQueryWrapper<PaperStateEventPO>()
            .eq(PaperStateEventPO::getToState, state.name()));
    }

    private PaperStateEvent toDomain(PaperStateEventPO po) {
        StateTransitionContext context = new StateTransitionContext(
            po.getOperator(),
            po.getReason(),
            po.getOccurredAt(),
            null
        );
        return PaperStateEvent.create(
            PaperId.of(po.getPaperId()),
            PaperState.valueOf(po.getFromState()),
            PaperState.valueOf(po.getToState()),
            context
        );
    }
}

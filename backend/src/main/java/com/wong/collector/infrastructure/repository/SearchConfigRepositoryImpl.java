package com.wong.collector.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.wong.collector.domain.search.model.SearchConfig;
import com.wong.collector.domain.search.model.SearchConfigId;
import com.wong.collector.domain.search.model.SearchStatus;
import com.wong.collector.domain.search.repository.SearchConfigRepository;
import com.wong.collector.infrastructure.persistence.converter.SearchConfigConverter;
import com.wong.collector.infrastructure.persistence.mapper.SearchConfigMapper;
import com.wong.collector.infrastructure.persistence.po.SearchConfigPO;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class SearchConfigRepositoryImpl implements SearchConfigRepository {

    private final SearchConfigMapper mapper;
    private final SearchConfigConverter converter;

    @Override
    public Optional<SearchConfig> findById(SearchConfigId id) {
        if (id == null) {
            return Optional.empty();
        }
        SearchConfigPO po = mapper.selectById(id.getValue());
        return Optional.ofNullable(converter.toAggregate(po));
    }

    @Override
    public SearchConfig save(SearchConfig config) {
        SearchConfigPO po = converter.toPO(config);
        if (po.getId() == null) {
            mapper.insert(po);
        } else {
            mapper.updateById(po);
        }
        return converter.toAggregate(mapper.selectById(po.getId()));
    }

    @Override
    public void delete(SearchConfigId id) {
        if (id != null) {
            mapper.deleteById(id.getValue());
        }
    }

    @Override
    public List<SearchConfig> findByStatus(SearchStatus status) {
        LambdaQueryWrapper<SearchConfigPO> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(SearchConfigPO::getSearchStatus, status.name());
        }
        return mapper.selectList(wrapper).stream()
            .map(converter::toAggregate)
            .collect(Collectors.toList());
    }

    @Override
    public List<SearchConfig> findAll() {
        return mapper.selectList(new LambdaQueryWrapper<>()).stream()
            .map(converter::toAggregate)
            .collect(Collectors.toList());
    }

    @Override
    public List<SearchConfig> findDueConfigs(LocalDateTime deadline) {
        LambdaQueryWrapper<SearchConfigPO> wrapper = new LambdaQueryWrapper<SearchConfigPO>()
            .eq(SearchConfigPO::getSearchStatus, SearchStatus.ACTIVE.name())
            .and(w -> w.isNull(SearchConfigPO::getNextRunTime)
                .or().le(SearchConfigPO::getNextRunTime, deadline));
        return mapper.selectList(wrapper).stream()
            .map(converter::toAggregate)
            .collect(Collectors.toList());
    }
}

package com.wong.collector.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wong.collector.infrastructure.persistence.po.SearchConfigPO;

@Mapper
public interface SearchConfigMapper extends BaseMapper<SearchConfigPO> {
}

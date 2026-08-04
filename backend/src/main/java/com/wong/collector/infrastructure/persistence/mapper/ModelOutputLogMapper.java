package com.wong.collector.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wong.collector.infrastructure.persistence.po.ModelOutputLogPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModelOutputLogMapper extends BaseMapper<ModelOutputLogPO> {
}


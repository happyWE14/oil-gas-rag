package com.wong.collector.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wong.collector.infrastructure.persistence.po.EventOutboxPO;

@Mapper
public interface EventOutboxMapper extends BaseMapper<EventOutboxPO> {
}

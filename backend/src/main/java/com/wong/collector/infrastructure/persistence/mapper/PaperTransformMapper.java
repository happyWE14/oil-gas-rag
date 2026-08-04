package com.wong.collector.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wong.collector.infrastructure.persistence.po.PaperTransformPO;

@Mapper
public interface PaperTransformMapper extends BaseMapper<PaperTransformPO> {
}

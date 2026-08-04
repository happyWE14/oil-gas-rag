// MaterialMetricRepository.java
package com.wong.collector.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wong.collector.infrastructure.persistence.po.MaterialMetricPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MaterialMetricRepository extends BaseMapper<MaterialMetricPO> {
}

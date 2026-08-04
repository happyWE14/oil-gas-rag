package com.wong.collector.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wong.collector.infrastructure.persistence.po.MaterialMetricPO;

import java.util.List;

@Mapper
public interface MaterialMetricMapper extends BaseMapper<MaterialMetricPO> {

    void deleteByPaperId(@Param("paperId") String paperId);

    void batchInsert(@Param("list") java.util.List<MaterialMetricPO> list);


    // 迁移用：清空表
    void truncateTable();




}

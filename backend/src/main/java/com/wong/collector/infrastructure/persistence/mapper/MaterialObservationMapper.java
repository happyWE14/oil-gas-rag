package com.wong.collector.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wong.collector.infrastructure.persistence.po.MaterialObservationPO;

import java.util.List;

@Mapper
public interface MaterialObservationMapper extends BaseMapper<MaterialObservationPO> {

    void deleteByPaperId(@Param("paperId") String paperId);

    void batchInsert(@Param("list") java.util.List<MaterialObservationPO> list);


    void truncateTable();
}

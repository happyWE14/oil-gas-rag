package com.wong.collector.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wong.collector.infrastructure.persistence.po.MaterialExtractionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MaterialExtractionRepository extends BaseMapper<MaterialExtractionPO> {

    @Select("SELECT * FROM material_extraction WHERE paper_id = #{paperId}")
    List<MaterialExtractionPO> findByPaperId(@Param("paperId") String paperId);
}

package com.wong.collector.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wong.collector.infrastructure.persistence.po.PaperCitationPO;

@Mapper
public interface PaperCitationMapper extends BaseMapper<PaperCitationPO> {

    @Select("select count(1) from paper_citation where citing_paper_id = #{paperId}")
    Long countByCitingPaperId(String paperId);
}


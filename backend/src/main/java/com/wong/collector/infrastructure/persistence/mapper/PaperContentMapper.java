package com.wong.collector.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wong.collector.infrastructure.persistence.po.PaperContentPO;

@Mapper
public interface PaperContentMapper extends BaseMapper<PaperContentPO> {

    @Select("select max(version) from paper_content where paper_id = #{paperId}")
    Integer findMaxVersion(String paperId);
}

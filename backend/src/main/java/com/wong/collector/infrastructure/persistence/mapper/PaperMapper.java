package com.wong.collector.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wong.collector.infrastructure.persistence.po.PaperPO;

@Mapper
public interface PaperMapper extends BaseMapper<PaperPO> {

    @Select("""
        select paper_id
        from paper
        where deleted = false
          and doi is not null
          and lower(doi) = lower(#{doi})
        limit 1
        """)
    String findPaperIdByDoi(String doi);

    @Update("""
        update paper
        set reference_count = #{referenceCount},
            update_time = CURRENT_TIMESTAMP
        where paper_id = #{paperId}
        """)
    int updateReferenceCount(@Param("paperId") String paperId, @Param("referenceCount") Integer referenceCount);
}

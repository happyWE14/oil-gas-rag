package com.wong.collector.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dtflys.forest.annotation.Query;
import com.wong.collector.infrastructure.persistence.po.PaperPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Mapper
public interface PaperRepository extends BaseMapper<PaperPO> {

    @Select("SELECT paper_id, title, authors, doi, year_published, abstract_content " +
            "FROM paper WHERE paper_id = #{paperId}")
    PaperPO findById(@Param("paperId") String paperId);

    @Select("<script>" +
            "SELECT paper_id, title, authors, doi, year_published, abstract_content FROM paper " +
            "WHERE paper_id IN " +
            "<foreach collection='paperIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<PaperPO> findByIds(@Param("paperIds") List<String> paperIds);

    @Query("SELECT p.paperId FROM PaperPO p WHERE p.deleted = false")
    List<String> findAllPaperIds();
    /**
     * 统计未删除的论文总数
     */
    @Query("SELECT COUNT(p) FROM PaperPO p WHERE p.deleted = false")
    long count();

    // 统计总数
    @Query("SELECT COUNT(p) FROM PaperPO p WHERE p.deleted = false")
    long countActive();


    // 或者分页版本（防止内存爆炸）
    @Query("SELECT p.paperId FROM PaperPO p WHERE p.deleted = false")
    Page<String> findAllPaperIds(Pageable pageable);
}

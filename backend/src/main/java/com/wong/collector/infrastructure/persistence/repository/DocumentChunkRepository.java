package com.wong.collector.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wong.collector.infrastructure.persistence.po.DocumentChunkPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentChunkRepository extends BaseMapper<DocumentChunkPO> {

    /**
     * 查询论文的第一个 chunk（用于获取代表向量）
     */
    @Select("SELECT * FROM document_chunk WHERE paper_id = #{paperId} ORDER BY order_no LIMIT 1")
    DocumentChunkPO findFirstByPaperId(@Param("paperId") String paperId);

    /**
     * 根据 chunk_ids 查询（注意：id 是 Long 类型）
     */
    @Select("<script>" +
            "SELECT * FROM document_chunk WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<DocumentChunkPO> findByIds(@Param("ids") List<Long> ids);

    @Select("SELECT * FROM document_chunk WHERE paper_id = #{paperId} ORDER BY order_no")
    List<DocumentChunkPO> findByPaperId(@Param("paperId") String paperId);

}

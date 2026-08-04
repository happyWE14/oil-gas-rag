package com.wong.collector.infrastructure.persistence.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wong.collector.infrastructure.persistence.po.DocumentChunkPO;
import com.wong.collector.infrastructure.persistence.po.IndexedDocumentPO;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunkPO> {
    int upsertByPaperAndOrder(DocumentChunkPO po);

    List<DocumentChunkPO> searchTopKByVector(@Param("paperId") String paperId,
                                             @Param("queryVector") List<Double> queryVector,
                                             @Param("limit") int limit);

    List<IndexedDocumentPO> listIndexedDocuments();

    /**
     * 根据 ID 列表查询，支持 String 类型（适配 varchar 主键）
     */
    @Select("<script>" +
            "SELECT id, paper_id, order_no, content, embedding_model FROM document_chunk " +
            "WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<DocumentChunkPO> selectByIds(@Param("ids") List<String> ids);
}

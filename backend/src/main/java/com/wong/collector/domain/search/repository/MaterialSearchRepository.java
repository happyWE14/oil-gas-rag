package com.wong.collector.domain.search.repository;

import com.wong.collector.domain.search.model.MaterialSearchCriteria;
import com.wong.collector.infrastructure.search.document.MaterialEsDocument;
import org.springframework.data.domain.Page;

import java.util.List;

public interface MaterialSearchRepository {

    /**
     * 保存单个文档
     */
    void save(MaterialEsDocument document);

    /**
     * 批量保存文档
     */
    void bulkSave(List<MaterialEsDocument> documents);

    /**
     * 搜索材料（支持向量和文本混合检索）
     */
    Page<MaterialEsDocument> search(MaterialSearchCriteria criteria);

    /**
     * 关键词建议（自动补全）
     */
    List<String> suggestKeywords(String keyword, int size);

    // 如果你是用自定义实现，需要添加：
    default void saveAll(List<MaterialEsDocument> documents) {
        // 批量保存逻辑
    }
}

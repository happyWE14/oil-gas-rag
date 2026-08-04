package com.wong.collector.domain.paper.repository;

import com.wong.collector.domain.paper.model.PaperId;

/**
 * 论文引用关系仓储（citing -> cited）。
 */
public interface PaperCitationRepository {

    /**
     * 幂等写入引用关系（依赖 DB 唯一约束去重）。若已存在则返回 false。
     */
    boolean insertIgnore(PaperId citingPaperId, PaperId citedPaperId);

    /**
     * 统计 references 数量：以 paper 为 citing 方的边数量（paper -> *）。
     */
    long countReferences(PaperId paperId);
}


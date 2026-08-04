package com.wong.collector.domain.paper.repository;

import java.util.Optional;

import com.wong.collector.domain.paper.model.PaperContent;
import com.wong.collector.domain.paper.model.PaperId;

public interface PaperContentRepository {

    /**
     * 保存新版本正文内容，版本号由实现生成。
     */
    PaperContent saveNewVersion(PaperContent content);

    Optional<PaperContent> findLatest(PaperId paperId);
}

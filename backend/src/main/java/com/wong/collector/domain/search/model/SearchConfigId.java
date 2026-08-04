package com.wong.collector.domain.search.model;

import com.wong.collector.domain.common.valueobject.BaseEntityId;

/**
 * 搜索配置ID值对象。
 */
public class SearchConfigId extends BaseEntityId<Long> {

    private SearchConfigId(Long value) {
        super(value);
    }

    public static SearchConfigId of(Long value) {
        return new SearchConfigId(value);
    }
}

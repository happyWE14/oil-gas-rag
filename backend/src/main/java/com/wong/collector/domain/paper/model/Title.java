package com.wong.collector.domain.paper.model;

import java.util.Objects;

import com.wong.collector.domain.common.exception.DomainException;

/**
 * 论文标题值对象。
 */
public record Title(String value) {

    public Title {
        if (value == null || value.isBlank()) {
            throw new DomainException("Paper title cannot be blank");
        }
    }

    public String normalized() {
        return value.trim();
    }

    public boolean containsKeyword(String keyword) {
        return keyword != null && normalized().toLowerCase().contains(keyword.toLowerCase());
    }

    @Override
    public String toString() {
        return normalized();
    }
}

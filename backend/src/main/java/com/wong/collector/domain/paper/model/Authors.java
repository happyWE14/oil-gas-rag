package com.wong.collector.domain.paper.model;

import java.util.List;

import com.wong.collector.domain.common.exception.DomainException;

/**
 * 作者列表，保持不可变。
 */
public final class Authors {

    private final List<String> names;

    private Authors(List<String> names) {
        if (names == null || names.isEmpty()) {
            throw new DomainException("Authors cannot be empty");
        }
        this.names = names.stream()
            .map(String::trim)
            .filter(name -> !name.isEmpty())
            .toList();
        if (this.names.isEmpty()) {
            throw new DomainException("Authors cannot be empty");
        }
    }

    public static Authors of(List<String> authorNames) {
        return new Authors(authorNames);
    }

    public List<String> getNames() {
        return names;
    }

    public String asString() {
        return String.join(", ", names);
    }
}

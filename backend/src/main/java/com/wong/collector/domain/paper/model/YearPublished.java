package com.wong.collector.domain.paper.model;

import java.time.Year;

import com.wong.collector.domain.common.exception.DomainException;

/**
 * 发表年份值对象。
 */
public record YearPublished(int value) {

    public YearPublished {
        int current = Year.now().getValue();
        if (value < 1900 || value > current + 1) {
            throw new DomainException("Illegal publish year: " + value);
        }
    }
}

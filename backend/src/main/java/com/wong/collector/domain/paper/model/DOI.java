package com.wong.collector.domain.paper.model;

import java.util.regex.Pattern;

import com.wong.collector.domain.common.exception.DomainException;

/**
 * DOI 值对象。
 */
public record DOI(String value) {

    private static final Pattern DOI_PATTERN = Pattern.compile("10.\\d{4,9}/[-._;()/:A-Z0-9]+", Pattern.CASE_INSENSITIVE);

    public DOI {
        if (value == null || value.isBlank()) {
            throw new DomainException("DOI cannot be blank");
        }
        if (!DOI_PATTERN.matcher(value).matches()) {
            throw new DomainException("Invalid DOI format: " + value);
        }
    }
}

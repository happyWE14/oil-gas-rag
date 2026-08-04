package com.wong.collector.domain.paper.model;

import java.util.UUID;

import com.wong.collector.domain.common.exception.DomainException;
import com.wong.collector.domain.common.valueobject.BaseEntityId;

/**
 * 论文聚合根的标识。
 */
public class PaperId extends BaseEntityId<String> {

    private PaperId(String value) {
        super(value);
    }

    public static PaperId newId() {
        return new PaperId(UUID.randomUUID().toString());
    }

    public static PaperId of(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException("PaperId cannot be blank");
        }
        return new PaperId(value);
    }


}

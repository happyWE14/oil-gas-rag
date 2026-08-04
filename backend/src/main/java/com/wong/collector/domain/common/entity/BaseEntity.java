package com.wong.collector.domain.common.entity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.wong.collector.domain.common.valueobject.BaseEntityId;

/**
 * 领域实体基类，包含审计字段
 */
public abstract class BaseEntity<ID extends BaseEntityId<?>> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private ID id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    protected BaseEntity() {
    }

    protected BaseEntity(ID id) {
        this.id = id;
    }

    public ID getId() {
        return id;
    }

    protected void setId(ID id) {
        this.id = id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}

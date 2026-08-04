package com.wong.collector.domain.common.valueobject;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

import com.wong.collector.domain.common.exception.DomainException;

/**
 * 值对象 ID 的通用基类，封装非空校验与相等性判断。
 *
 * @param <T> ID 对应的原始类型
 */
public abstract class BaseEntityId<T extends Serializable> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final T value;

    protected BaseEntityId(T value) {
        if (value == null) {
            throw new DomainException("ID value cannot be null");
        }
        this.value = value;
    }

    public T getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BaseEntityId<?> that = (BaseEntityId<?>) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}

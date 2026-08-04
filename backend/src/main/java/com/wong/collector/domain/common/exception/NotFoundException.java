package com.wong.collector.domain.common.exception;

/**
 * 领域对象未找到时抛出。
 */
public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(message);
    }
}

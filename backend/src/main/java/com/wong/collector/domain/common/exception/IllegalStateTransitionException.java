package com.wong.collector.domain.common.exception;

/**
 * 状态机非法流转异常。
 */
public class IllegalStateTransitionException extends DomainException {

    public IllegalStateTransitionException(String message) {
        super(message);
    }
}

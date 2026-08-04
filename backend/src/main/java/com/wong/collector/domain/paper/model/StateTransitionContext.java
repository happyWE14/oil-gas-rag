package com.wong.collector.domain.paper.model;

import java.time.LocalDateTime;

/**
 * 状态转换上下文，记录操作者和原因。
 */
public record StateTransitionContext(
        String operator,
        String reason,
        LocalDateTime transitionTime,
        String metadata) {

    public static StateTransitionContext system(String reason) {
        return new StateTransitionContext("system", reason, LocalDateTime.now(), null);
    }
}

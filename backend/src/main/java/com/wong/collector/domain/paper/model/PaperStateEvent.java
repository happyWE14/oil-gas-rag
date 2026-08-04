package com.wong.collector.domain.paper.model;

import java.time.LocalDateTime;

import com.wong.collector.domain.common.entity.BaseEntity;

/**
 * 状态流转记录实体。
 */
public class PaperStateEvent extends BaseEntity<PaperId> {

    private final PaperState fromState;
    private final PaperState toState;
    private final String operator;
    private final String reason;
    private final LocalDateTime occurredAt;

    private PaperStateEvent(PaperId paperId, PaperState fromState, PaperState toState,
                            String operator, String reason, LocalDateTime occurredAt) {
        super(paperId);
        this.fromState = fromState;
        this.toState = toState;
        this.operator = operator;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public static PaperStateEvent create(PaperId paperId, PaperState fromState,
                                         PaperState toState, StateTransitionContext context) {
        return new PaperStateEvent(paperId, fromState, toState,
            context.operator(), context.reason(), context.transitionTime());
    }

    public PaperState getFromState() {
        return fromState;
    }

    public PaperState getToState() {
        return toState;
    }

    public String getOperator() {
        return operator;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}

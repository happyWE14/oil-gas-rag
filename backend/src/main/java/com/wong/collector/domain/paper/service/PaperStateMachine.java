package com.wong.collector.domain.paper.service;

import java.util.List;

import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.domain.paper.model.PaperState;
import com.wong.collector.domain.paper.model.PaperStateEvent;
import com.wong.collector.domain.paper.model.StateTransitionContext;

/**
 * 论文状态机接口。
 */
public interface PaperStateMachine {

    PaperStateEvent transition(Paper paper, PaperState targetState, StateTransitionContext context);

    boolean isValidTransition(PaperState from, PaperState to);

    List<PaperState> getNextStates(PaperState currentState);

    PaperStateEvent retry(Paper paper, StateTransitionContext context);

    PaperStateEvent abandon(Paper paper, String reason);
}

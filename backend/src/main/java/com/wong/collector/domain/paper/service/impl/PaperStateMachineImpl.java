package com.wong.collector.domain.paper.service.impl;

import java.util.List;

import com.wong.collector.domain.paper.model.DownloadInfo;
import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.domain.paper.model.PaperState;
import com.wong.collector.domain.paper.model.PaperStateEvent;
import com.wong.collector.domain.paper.model.PaperStateTransition;
import com.wong.collector.domain.paper.model.StateTransitionContext;
import com.wong.collector.domain.paper.repository.PaperStateEventRepository;
import com.wong.collector.domain.paper.service.PaperStateMachine;

/**
 * 默认状态机实现，负责记录状态流转事件。
 */
public class PaperStateMachineImpl implements PaperStateMachine {

    private final PaperStateEventRepository repository;

    public PaperStateMachineImpl(PaperStateEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public PaperStateEvent transition(Paper paper, PaperState targetState, StateTransitionContext context) {
        PaperState currentState = paper.getState();
        
        if (currentState == targetState) {
            return null;
        }

        if (currentState.isFinalState() && currentState != targetState) {
            return null;
        }

        if (!isValidTransition(currentState, targetState)) {
            return null;
        }

        PaperStateEvent event = PaperStateEvent.create(paper.getId(), currentState, targetState, context);
        repository.save(event);
        return event;
    }

    @Override
    public boolean isValidTransition(PaperState from, PaperState to) {
        return PaperStateTransition.isValid(from, to);
    }

    @Override
    public List<PaperState> getNextStates(PaperState currentState) {
        return PaperStateTransition.nextStates(currentState);
    }

    @Override
    public PaperStateEvent retry(Paper paper, StateTransitionContext context) {
        PaperState retryState = determineRetryState(paper);
        return transition(paper, retryState, context);
    }

    @Override
    public PaperStateEvent abandon(Paper paper, String reason) {
        return transition(paper, PaperState.ABANDONED, StateTransitionContext.system(reason));
    }

    private PaperState determineRetryState(Paper paper) {
        DownloadInfo downloadInfo = paper.getDownloadInfo();
        if (downloadInfo != null && downloadInfo.isFailed()) {
            return PaperState.DISCOVERED;
        }
        if (paper.getTransformInfo() != null && paper.getTransformInfo().isFailed()) {
            return PaperState.DOWNLOADED;
        }
        return PaperState.DISCOVERED;
    }
}

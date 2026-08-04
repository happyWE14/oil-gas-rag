package com.wong.collector.domain.paper.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PaperStateTransitionTest {

    @Test
    void keepsTheHappyPathAndTerminalStatesExplicit() {
        assertTrue(PaperStateTransition.isValid(PaperState.TRANSFORMED, PaperState.EMBEDDING));
        assertTrue(PaperStateTransition.isValid(PaperState.IRRELEVANT, PaperState.COMPLETED));
        assertFalse(PaperState.IRRELEVANT.isFinalState());
        assertFalse(PaperStateTransition.isValid(PaperState.COMPLETED, PaperState.DISCOVERED));
    }
}

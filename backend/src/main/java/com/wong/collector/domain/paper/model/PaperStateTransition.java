package com.wong.collector.domain.paper.model;

import static java.util.Map.entry;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.wong.collector.domain.common.exception.DomainException;

/**
 * 状态转换规则表。
 */
public final class PaperStateTransition {

    private static final Map<PaperState, List<PaperState>> VALID_TRANSITIONS;

    static {
        Map<PaperState, List<PaperState>> transitions = new EnumMap<>(PaperState.class);
        transitions.put(PaperState.DISCOVERED, List.of(
            PaperState.PRE_ANALYZING,
            PaperState.FAILED,
            PaperState.ABANDONED
        ));
        transitions.put(PaperState.PRE_ANALYZING, List.of(
            PaperState.PRE_RELEVANT,
            PaperState.IRRELEVANT,
            PaperState.FAILED
        ));
        transitions.put(PaperState.PRE_RELEVANT, List.of(
            PaperState.DOWNLOADING,
            PaperState.FAILED
        ));
        transitions.put(PaperState.IRRELEVANT, List.of(PaperState.COMPLETED));
        transitions.put(PaperState.DOWNLOADING, List.of(
            PaperState.DOWNLOADED,
            PaperState.FAILED
        ));
        transitions.put(PaperState.DOWNLOADED, List.of(
            PaperState.TRANSFORMING,
            PaperState.FAILED
        ));
        transitions.put(PaperState.TRANSFORMING, List.of(
            PaperState.TRANSFORMED,
            PaperState.FAILED
        ));
        transitions.put(PaperState.TRANSFORMED, List.of(
            PaperState.EMBEDDING,
            PaperState.FAILED
        ));
        transitions.put(PaperState.EMBEDDING, List.of(
            PaperState.EMBEDDING_COMPLETED,
            PaperState.FAILED
        ));
        transitions.put(PaperState.EMBEDDING_COMPLETED, List.of(
            PaperState.EXTRACTING,
            PaperState.FAILED
        ));
        transitions.put(PaperState.EXTRACTING, List.of(
            PaperState.MATERIAL_EXTRACTED,
            PaperState.FAILED
        ));
        transitions.put(PaperState.MATERIAL_EXTRACTED, List.of(PaperState.COMPLETED));
        // FAILED 是终态，只能转到 ABANDONED（人工放弃）
        // 任务重试由 TaskRecord 负责，Paper 不再参与重试状态机
        transitions.put(PaperState.FAILED, List.of(PaperState.ABANDONED));
        transitions.put(PaperState.COMPLETED, List.of());
        transitions.put(PaperState.ABANDONED, List.of());
        VALID_TRANSITIONS = Collections.unmodifiableMap(transitions);
    }

    private PaperStateTransition() {
    }

    public static boolean isValid(PaperState from, PaperState to) {
        if (from == null || to == null) {
            return false;
        }
        List<PaperState> allowed = VALID_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    public static List<PaperState> nextStates(PaperState state) {
        return VALID_TRANSITIONS.getOrDefault(state, List.of());
    }

    public static PaperState ensureTransition(PaperState from, PaperState to) {
        if (!isValid(from, to)) {
            throw new DomainException("Invalid transition from " + from + " to " + to);
        }
        return to;
    }
}

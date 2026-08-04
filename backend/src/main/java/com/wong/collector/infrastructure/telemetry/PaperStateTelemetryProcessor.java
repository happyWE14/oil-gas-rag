package com.wong.collector.infrastructure.telemetry;

import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.wong.collector.infrastructure.persistence.mapper.PaperStateEventMapper;
import com.wong.collector.infrastructure.persistence.po.PaperStateEventPO;

import lombok.RequiredArgsConstructor;

/**
 * 将论文状态流转落表 paper_state_event。
 */
@Component
@RequiredArgsConstructor
public class PaperStateTelemetryProcessor implements TelemetryProcessor {

    private final PaperStateEventMapper mapper;

    @Override
    public boolean supports(TelemetryEvent event) {
        return event instanceof PaperStateTelemetryEvent;
    }

    @Override
    public void process(TelemetryEvent event) {
        if (!(event instanceof PaperStateTelemetryEvent stateEvent)) {
            return;
        }
        if (!StringUtils.isNotBlank(stateEvent.getPaperId())) {
            return;
        }
        PaperStateEventPO po = new PaperStateEventPO();
        po.setPaperId(stateEvent.getPaperId());
        po.setFromState(stateEvent.getFromState());
        po.setToState(stateEvent.getToState());
        po.setOperator(stateEvent.getOperator());
        po.setReason(stateEvent.getReason());
        Instant occurred = stateEvent.getOccurredAt();
        po.setOccurredAt(occurred == null ? null : occurred.atZone(ZoneOffset.UTC).toLocalDateTime());
        mapper.insert(po);
    }
}

package com.wong.collector.application.event.handler.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wong.collector.application.event.handler.PreAnalysisHandler;
import com.wong.collector.domain.paper.event.PaperDiscoveredEvent;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "paper.event.mode", havingValue = "rocketmq")
@RocketMQMessageListener(
    topic = "paper-lifecycle-events",
    selectorExpression = "PaperDiscoveredEvent",
    consumerGroup = "paper-pre-analysis-consumer"
)
public class PreAnalysisMqHandler extends AbstractPaperEventMqListener<PaperDiscoveredEvent> {

    private final PreAnalysisHandler delegate;

    public PreAnalysisMqHandler(ObjectMapper objectMapper, PreAnalysisHandler delegate) {
        super(objectMapper, PaperDiscoveredEvent.class);
        this.delegate = delegate;
    }

    @Override
    protected void handleEvent(PaperDiscoveredEvent event) {
        delegate.onPaperDiscovered(event);
    }
}

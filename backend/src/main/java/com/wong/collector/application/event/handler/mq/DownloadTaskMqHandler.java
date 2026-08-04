package com.wong.collector.application.event.handler.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wong.collector.application.event.handler.PipelineTaskEventHandler;
import com.wong.collector.domain.paper.event.PaperPreRelevantEvent;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "paper.event.mode", havingValue = "rocketmq")
@RocketMQMessageListener(
    topic = "paper-lifecycle-events",
    selectorExpression = "PaperPreRelevantEvent",
    consumerGroup = "paper-download-consumer"
)
public class DownloadTaskMqHandler extends AbstractPaperEventMqListener<PaperPreRelevantEvent> {

    private final PipelineTaskEventHandler delegate;

    public DownloadTaskMqHandler(ObjectMapper objectMapper, PipelineTaskEventHandler delegate) {
        super(objectMapper, PaperPreRelevantEvent.class);
        this.delegate = delegate;
    }

    @Override
    protected void handleEvent(PaperPreRelevantEvent event) {
        delegate.handle(event);
    }
}

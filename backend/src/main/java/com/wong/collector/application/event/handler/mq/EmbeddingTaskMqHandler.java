package com.wong.collector.application.event.handler.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wong.collector.application.event.handler.PipelineTaskEventHandler;
import com.wong.collector.domain.paper.event.PaperTransformedEvent;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "paper.event.mode", havingValue = "rocketmq")
@RocketMQMessageListener(
    topic = "paper-lifecycle-events",
    selectorExpression = "PaperTransformedEvent",
    consumerGroup = "paper-embedding-consumer"
)
public class EmbeddingTaskMqHandler extends AbstractPaperEventMqListener<PaperTransformedEvent> {

    private final PipelineTaskEventHandler delegate;

    public EmbeddingTaskMqHandler(ObjectMapper objectMapper, PipelineTaskEventHandler delegate) {
        super(objectMapper, PaperTransformedEvent.class);
        this.delegate = delegate;
    }

    @Override
    protected void handleEvent(PaperTransformedEvent event) {
        delegate.handle(event);
    }
}

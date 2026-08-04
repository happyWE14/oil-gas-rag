package com.wong.collector.application.event.handler.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wong.collector.application.event.handler.PipelineTaskEventHandler;
import com.wong.collector.domain.paper.event.PaperEmbeddingCompletedEvent;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "paper.event.mode", havingValue = "rocketmq")
@RocketMQMessageListener(
    topic = "paper-lifecycle-events",
    selectorExpression = "PaperEmbeddingCompletedEvent",
    consumerGroup = "paper-material-consumer"
)
public class MaterialExtractionMqHandler extends AbstractPaperEventMqListener<PaperEmbeddingCompletedEvent> {

    private final PipelineTaskEventHandler delegate;

    public MaterialExtractionMqHandler(ObjectMapper objectMapper, PipelineTaskEventHandler delegate) {
        super(objectMapper, PaperEmbeddingCompletedEvent.class);
        this.delegate = delegate;
    }

    @Override
    protected void handleEvent(PaperEmbeddingCompletedEvent event) {
        delegate.handle(event);
    }
}

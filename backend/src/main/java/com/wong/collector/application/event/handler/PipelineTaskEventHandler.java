package com.wong.collector.application.event.handler;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.wong.collector.application.workflow.pipeline.PipelineOrchestrator;
import com.wong.collector.domain.paper.event.PaperDownloadedEvent;
import com.wong.collector.domain.paper.event.PaperEmbeddingCompletedEvent;
import com.wong.collector.domain.paper.event.PaperPreRelevantEvent;
import com.wong.collector.domain.paper.event.PaperTransformedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PipelineTaskEventHandler {

    private final PipelineOrchestrator pipelineOrchestrator;

    @EventListener
    public void handle(PaperPreRelevantEvent event) {
        pipelineOrchestrator.handleEvent(event.getClass().getSimpleName(), event.getPaperId().getValue());
    }

    @EventListener
    public void handle(PaperDownloadedEvent event) {
        pipelineOrchestrator.handleEvent(event.getClass().getSimpleName(), event.getPaperId().getValue());
    }

    @EventListener
    public void handle(PaperTransformedEvent event) {
        pipelineOrchestrator.handleEvent(event.getClass().getSimpleName(), event.getPaperId().getValue());
    }

    @EventListener
    public void handle(PaperEmbeddingCompletedEvent event) {
        pipelineOrchestrator.handleEvent(event.getClass().getSimpleName(), event.getPaperId().getValue());
    }
}

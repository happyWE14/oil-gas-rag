package com.wong.collector.interfaces.rest.controller;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wong.collector.application.service.rag.CustomRagService;
import com.wong.collector.application.service.rag.CustomRagService.QueryResult;
import com.wong.collector.application.workflow.rag.DocumentContextRetriever.IndexedDocument;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rag.custom", name = "enabled", havingValue = "true")
public class CustomRagController {

    private final CustomRagService customRagService;

    @GetMapping("/documents")
    public List<IndexedDocument> listDocuments() {
        return customRagService.listDocuments();
    }

    @PostMapping("/query")
    public QueryResult query(@RequestBody @Valid QueryRequest request) {
        return customRagService.query(request.documentId(), request.question(), request.topK());
    }

    public record QueryRequest(
            @NotBlank @Size(max = 255) String documentId,
            @NotBlank @Size(max = 2000) String question,
            @Min(1) @Max(20) Integer topK) {}
}

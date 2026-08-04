package com.wong.collector.application.workflow.transform.ocr;

import com.wong.collector.domain.paper.model.TransformProvider;

import java.io.InputStream;

public interface OcrTransformGateway {

    boolean supports(TransformProvider provider);

    OcrResult transform(String paperId, String downloadUrl, boolean saveMarkdown);

    // 新方法：直接处理 InputStream
    OcrResult transformStream(String paperId, InputStream pdfStream, boolean saveMarkdown);

    record OcrResult(String textContent, String markdownUrl, String checksum) {}
}

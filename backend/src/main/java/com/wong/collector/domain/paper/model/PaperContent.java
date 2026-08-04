package com.wong.collector.domain.paper.model;

import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.Objects;

import com.wong.collector.domain.common.exception.DomainException;

import lombok.Getter;

/**
 * 论文正文内容。
 */
@Getter
public class PaperContent {

    private PaperId paperId;
    private int version;
    private TextSourceType sourceType;
    private String charset;
    private String checksum;
    private long size;
    private String content;
    private LocalDateTime createdAt;

    private PaperContent() {
    }

    public static PaperContent create(PaperId paperId, String content,
                                      TextSourceType sourceType, String charset,
                                      String checksum, long size) {
        if (paperId == null) {
            throw new DomainException("paperId required for content");
        }
        if (content == null || content.isBlank()) {
            throw new DomainException("content required");
        }
        PaperContent pc = new PaperContent();
        pc.paperId = paperId;
        pc.content = content;
        pc.sourceType = sourceType == null ? TextSourceType.OTHER : sourceType;
        pc.charset = charset == null ? Charset.defaultCharset().name() : charset;
        pc.checksum = checksum;
        pc.size = size;
        pc.version = 0; // set by repository when persisted
        pc.createdAt = LocalDateTime.now();
        return pc;
    }

    public static PaperContent restore(PaperId paperId,
                                       int version,
                                       TextSourceType sourceType,
                                       String charset,
                                       String checksum,
                                       long size,
                                       String content,
                                       LocalDateTime createdAt) {
        PaperContent pc = new PaperContent();
        pc.paperId = Objects.requireNonNull(paperId, "paperId");
        pc.version = version;
        pc.sourceType = sourceType == null ? TextSourceType.OTHER : sourceType;
        pc.charset = charset;
        pc.checksum = checksum;
        pc.size = size;
        pc.content = content;
        pc.createdAt = createdAt;
        return pc;
    }
}

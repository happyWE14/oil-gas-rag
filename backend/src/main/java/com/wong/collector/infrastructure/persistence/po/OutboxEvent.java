package com.wong.collector.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.*;

import com.wong.collector.infrastructure.persistence.typehandler.JsonbTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("event_outbox")
public class OutboxEvent {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;
    private String eventType;
    private String aggregateId;

    // 关键修改：添加 typeHandler
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String payload;

    private String status; // PENDING/SENT/FAILED
    private Integer retryCount;
    private Integer maxRetries;
    private LocalDateTime sentAt;
    private LocalDateTime nextRetryAt;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

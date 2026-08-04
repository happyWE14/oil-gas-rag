package com.wong.collector.infrastructure.persistence.po;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("task_record")
public class TaskRecordPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("paper_id")
    private String paperId;

    @TableField("task_type")
    private String taskType;

    @TableField("task_name")
    private String taskName;

    @TableField("status")
    private String status;

    @TableField("description")
    private String description;

    @TableField(value = "start_time", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime startTime;

    @TableField(value = "end_time", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime endTime;

    @TableField(value = "duration_ms", updateStrategy = FieldStrategy.ALWAYS)
    private Long durationMs;

    @TableField(value = "error_message", updateStrategy = FieldStrategy.ALWAYS)
    private String errorMessage;

    @TableField(value = "error_category", updateStrategy = FieldStrategy.ALWAYS)
    private String errorCategory;

    @TableField(value = "error_reason", updateStrategy = FieldStrategy.ALWAYS)
    private String errorReason;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("max_retries")
    private Integer maxRetries;

    @TableField(value = "next_retry_at", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime nextRetryAt;

    @TableField("parameters")
    private String parameters;

    @TableField(value = "result", updateStrategy = FieldStrategy.ALWAYS)
    private String result;

    @TableField("created_by")
    private String createdBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

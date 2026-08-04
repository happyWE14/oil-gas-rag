package com.wong.collector.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("model_output_log")
public class ModelOutputLogPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("paper_id")
    private String paperId;

    @TableField("task_type")
    private String taskType;

    @TableField("model_name")
    private String modelName;

    @TableField("model_output")
    private String modelOutput;

    @TableField("create_time")
    private LocalDateTime createTime;
}


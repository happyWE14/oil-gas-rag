package com.wong.collector.infrastructure.persistence.po;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("paper_state_event")
public class PaperStateEventPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("paper_id")
    private String paperId;

    @TableField("from_state")
    private String fromState;

    @TableField("to_state")
    private String toState;

    @TableField("operator")
    private String operator;

    @TableField("reason")
    private String reason;

    @TableField("occurred_at")
    private LocalDateTime occurredAt;
}

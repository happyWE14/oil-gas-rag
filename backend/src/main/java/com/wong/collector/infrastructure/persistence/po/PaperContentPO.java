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
@TableName("paper_content")
public class PaperContentPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("paper_id")
    private String paperId;

    @TableField("version")
    private Integer version;

    @TableField("source_type")
    private String sourceType;

    @TableField("charset")
    private String charset;

    @TableField("checksum")
    private String checksum;

    @TableField("size")
    private Long size;

    @TableField("content")
    private String content;

    @TableField("created_at")
    private LocalDateTime createdAt;
}

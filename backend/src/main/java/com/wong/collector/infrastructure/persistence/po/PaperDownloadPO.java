package com.wong.collector.infrastructure.persistence.po;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("paper_download")
public class PaperDownloadPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("paper_id")
    private String paperId;

    @TableField("source_url")
    private String sourceUrl;

    @TableField("oss_url")
    private String ossUrl;

    @TableField("file_size")
    private Long fileSize;

    @TableField("try_times")
    private Integer tryTimes;

    @TableField("download_status")
    private String downloadStatus;

    @TableField("error_message")
    private String errorMessage;

    @TableField(value = "start_time", fill = FieldFill.INSERT)
    private LocalDateTime startTime;

    @TableField(value = "finish_time")
    private LocalDateTime finishTime;
}

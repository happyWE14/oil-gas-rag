package com.wong.collector.infrastructure.persistence.po;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("file_part_detail")
public class FilePartDetailPO {

    @TableId(type = IdType.INPUT)
    private String id;
    private String platform;
    private String uploadId;
    private String eTag;
    private Integer partNumber;
    private Long partSize;
    private String hashInfo;
    private LocalDateTime createTime;
}

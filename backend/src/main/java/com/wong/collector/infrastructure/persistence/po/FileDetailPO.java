package com.wong.collector.infrastructure.persistence.po;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("file_detail")
public class FileDetailPO {

    @TableId(type = IdType.INPUT)
    private String id;
    private String url;
    private Long size;
    private String filename;
    @TableField("original_filename")
    private String originalFilename;
    @TableField("base_path")
    private String basePath;
    private String path;
    private String ext;
    @TableField("content_type")
    private String contentType;
    private String platform;
    @TableField("th_url")
    private String thUrl;
    @TableField("th_filename")
    private String thFilename;
    @TableField("th_size")
    private Long thSize;
    @TableField("th_content_type")
    private String thContentType;
    @TableField("object_id")
    private String objectId;
    @TableField("object_type")
    private String objectType;
    private String metadata;
    @TableField("user_metadata")
    private String userMetadata;
    @TableField("th_metadata")
    private String thMetadata;
    @TableField("th_user_metadata")
    private String thUserMetadata;
    private String attr;
    @TableField("file_acl")
    private String fileAcl;
    @TableField("th_file_acl")
    private String thFileAcl;
    @TableField("hash_info")
    private String hashInfo;
    @TableField("upload_id")
    private String uploadId;
    @TableField("upload_status")
    private Integer uploadStatus;
    @TableField("create_time")
    private LocalDateTime createTime;
}

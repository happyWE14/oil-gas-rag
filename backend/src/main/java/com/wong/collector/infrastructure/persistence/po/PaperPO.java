package com.wong.collector.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("paper")
public class PaperPO {

    @TableId(type = IdType.INPUT)
    private String paperId;

    private String title;

    private String authors;

    private String doi;

    @TableField("year_published")
    private Integer yearPublished;

    @TableField("paper_source")
    private String paperSource;

    @TableField("abstract_content")
    private String abstractContent;

    @TableField("download_url")
    private String downloadUrl;

    @TableField("citation_count")
    private Integer citationCount;

    @TableField("reference_count")
    private Integer referenceCount;

    private String status;

    // 暂时移除乐观锁和逻辑删除注解
    private Long version;
    private Boolean deleted;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}

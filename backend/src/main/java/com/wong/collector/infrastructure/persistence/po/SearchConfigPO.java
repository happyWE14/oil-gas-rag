package com.wong.collector.infrastructure.persistence.po;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.apache.ibatis.type.JdbcType;

import com.wong.collector.infrastructure.persistence.typehandler.JsonbStringTypeHandler;

import lombok.Data;

@Data
@TableName(value = "paper_search_config", autoResultMap = true)
public class SearchConfigPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("platform")
    private String platform;

    @TableField("query")
    private String query;

    @TableField("current_page")
    private Integer currentPage;

    @TableField("page_size")
    private Integer pageSize;

    @TableField("total_page")
    private Integer totalPage;

    @TableField("total_count")
    private Integer totalCount;

    @TableField("search_fields")
    private String searchFields;

    @TableField("sort_order")
    private String sortOrder;

    @TableField("filters")
    private String filters;

    @TableField("search_status")
    private String searchStatus;

    @TableField("last_run_time")
    private LocalDateTime lastRunTime;

    @TableField("next_run_time")
    private LocalDateTime nextRunTime;

    @TableField("cron_expression")
    private String cronExpression;

    @TableField("max_empty_pages")
    private Integer maxEmptyPages;

    @TableField("empty_page_count")
    private Integer emptyPageCount;

    @TableField("last_failure_reason")
    private String lastFailureReason;

    @TableField(value = "query_snapshot", typeHandler = JsonbStringTypeHandler.class, jdbcType = JdbcType.OTHER)
    private String querySnapshot;

    @TableField(value = "keywords_snapshot", typeHandler = JsonbStringTypeHandler.class, jdbcType = JdbcType.OTHER)
    private String keywordsSnapshot;

    @TableField("template_name")
    private String templateName;

    @TableField("batch_id")
    private String batchId;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

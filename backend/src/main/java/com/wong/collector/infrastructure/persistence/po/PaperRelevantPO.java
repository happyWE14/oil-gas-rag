package com.wong.collector.infrastructure.persistence.po;

import java.io.Serial;
import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.apache.ibatis.type.JdbcType;

import com.wong.collector.infrastructure.persistence.typehandler.JsonbStringTypeHandler;

import lombok.Data;

@Data
@TableName(value = "paper_relevant", autoResultMap = true)
public class PaperRelevantPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("paper_id")
    private String paperId;

    @TableField("is_relevant")
    private Boolean relevant;

    @TableField("score")
    private Double score;

    @TableField("analyzer")
    private String analyzer;

    @TableField("reason")
    private String reason;

    /**
     * 模型原始JSON结果，兼容PG/MySQL的JSON或文本存储。
     */
    @TableField(value = "raw_result", typeHandler = JsonbStringTypeHandler.class, jdbcType = JdbcType.OTHER)
    private String rawResult;

    /**
     * 使用的关键词快照（JSON字符串）。
     */
    @TableField(value = "keywords", typeHandler = JsonbStringTypeHandler.class, jdbcType = JdbcType.OTHER)
    private String keywords;

    /**
     * 判定阈值。
     */
    @TableField("threshold_used")
    private Double thresholdUsed;

    /**
     * 模型是否认为可提取材料/化学性质信息。
     */
    @TableField("has_extractable_material_info")
    private Boolean hasExtractableMaterialInfo;
}

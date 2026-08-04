package com.wong.collector.infrastructure.persistence.po;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.wong.collector.infrastructure.persistence.typehandler.StringArrayTypeHandler;

import lombok.Data;
import org.apache.ibatis.type.JdbcType;

@Data
@TableName(value = "material_metric", autoResultMap = true)
public class MaterialMetricPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("paper_id")
    private String paperId;

    @TableField("material_key")
    private String materialKey;

    @TableField("metric_key")
    private String metricKey;

    @TableField("value_type")
    private String valueType;

    @TableField("value_num")
    private Double valueNum;

    @TableField("value_min")
    private Double valueMin;

    @TableField("value_max")
    private Double valueMax;

    @TableField("value_text")
    private String valueText;

    @TableField("unit")
    private String unit;

    @TableField("qualifier")
    private String qualifier;

    /**
     * ✅ 使用 MyBatis-Plus 内置的 JacksonTypeHandler
     * 自动处理 Map <-> PostgreSQL JSONB 的转换
     */
    @TableField(value = "conditions", typeHandler = JacksonTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Map<String, Object> conditions;

    @TableField("confidence")
    private Double confidence;

    @TableField(value = "chunk_ids", typeHandler = StringArrayTypeHandler.class, jdbcType = JdbcType.ARRAY)
    private String[] chunkIds;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}

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
@TableName(value = "material_extraction", autoResultMap = true)
public class MaterialExtractionPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("paper_id")
    private String paperId;

    @TableField("material")
    private String material;

    @TableField("property")
    private String property;

    @TableField("method")
    private String method;

    @TableField("confidence")
    private Double confidence;

    /**
     * 提取的完整JSON结果，保留灵活字段。
     */
    @TableField(value = "detail_json", typeHandler = JsonbStringTypeHandler.class, jdbcType = JdbcType.OTHER)
    private String detailJson;
}

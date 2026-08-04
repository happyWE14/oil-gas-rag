package com.wong.collector.infrastructure.persistence.po;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import org.apache.ibatis.type.JdbcType;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wong.collector.infrastructure.persistence.typehandler.StringArrayTypeHandler;

import lombok.Data;

@Data
@TableName(value = "material_observation", autoResultMap = true)
public class MaterialObservationPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("paper_id")
    private String paperId;

    @TableField("material_key")
    private String materialKey;

    @TableField("type")
    private String type;

    @TableField("content")
    private String content;

    @TableField("confidence")
    private Double confidence;

    @TableField(value = "chunk_ids", typeHandler = StringArrayTypeHandler.class, jdbcType = JdbcType.ARRAY)
    private String[] chunkIds;

    @TableField("create_time")
    private LocalDateTime createTime;
}

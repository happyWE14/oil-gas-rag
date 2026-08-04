package com.wong.collector.infrastructure.persistence.po;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.apache.ibatis.type.JdbcType;

import com.wong.collector.infrastructure.persistence.typehandler.PgVectorTypeHandler;

import lombok.Data;

@Data
@TableName(value = "document_chunk", autoResultMap = true)
public class DocumentChunkPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;  // 注意：是 Long 类型

    @TableField("paper_id")
    private String paperId;

    @TableField("order_no")
    private Integer orderNo;

    @TableField("content")
    private String content;

    @TableField("embedding_model")
    private String embeddingModel;

    @TableField(value = "vector", typeHandler = PgVectorTypeHandler.class, jdbcType = JdbcType.OTHER)
    private List<Double> vector;  // PG 读出的是 List<Double>

    /**
     * 转换为 ES 需要的 float[]（dense_vector 要求）
     */
    public float[] getVectorAsFloatArray() {
        if (vector == null || vector.isEmpty()) {
            return null;
        }
        // 转换为 float[] 并验证维度
        float[] result = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            result[i] = vector.get(i).floatValue();
        }
        return result;
    }

    /**
     * 获取向量的维度（应该是 2048）
     */
    public int getVectorDimension() {
        return vector == null ? 0 : vector.size();
    }

    public void setCreatedAt(LocalDateTime now) {

    }
}

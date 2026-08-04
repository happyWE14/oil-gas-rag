package com.wong.collector.infrastructure.search.document;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialEsDocument {

    @Id
    @Field(type = FieldType.Keyword)
    private String docId;

    @Field(type = FieldType.Object)
    private PaperInfo paper;

    @Field(type = FieldType.Object)
    private MaterialInfo material;

    @Field(type = FieldType.Nested)
    private List<Metric> metrics;

    @Field(type = FieldType.Nested)
    private List<Observation> observations;

    @Field(type = FieldType.Nested)
    private List<ChunkInfo> chunks;

    @Field(name = "embedding_vector", type = FieldType.Dense_Vector, dims = 2048)
    private float[] embeddingVector;

    @Field(type = FieldType.Object)
    private SyncMetadata syncMetadata;

    @Field(type = FieldType.Text, analyzer = "chemistry")
    private String fullTextSearch;

    // ========== 内部类定义 ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaperInfo {
        @Field(type = FieldType.Keyword)
        private String paperId;

        @Field(type = FieldType.Text, analyzer = "chemistry")
        private String title;

        @Field(type = FieldType.Text, analyzer = "chemistry")
        private String authors;

        @Field(type = FieldType.Integer)
        private Integer year;

        @Field(type = FieldType.Keyword)
        private String doi;

        @Field(type = FieldType.Text, analyzer = "chemistry")
        private String abstractContent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MaterialInfo {
        @Field(type = FieldType.Keyword)
        private String materialKey;

        @Field(type = FieldType.Text, analyzer = "chemistry", copyTo = "fullTextSearch")
        private String materialName;

        @Field(type = FieldType.Float)
        private Float confidence;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metric {
        @Field(type = FieldType.Keyword)
        private String metricKey;

        @Field(type = FieldType.Keyword)
        private String metricName;

        @Field(type = FieldType.Keyword)
        private String valueType;

        @Field(type = FieldType.Double)
        private Double valueNum;

        @Field(type = FieldType.Double)
        private Double valueMin;

        @Field(type = FieldType.Double)
        private Double valueMax;

        @Field(type = FieldType.Text)
        private String valueText;

        @Field(type = FieldType.Keyword)
        private String unit;

        // ✅ 关键修复：确保 qualifier 字段能被正确映射
        @Field(type = FieldType.Keyword)
        private String qualifier;

        @Field(type = FieldType.Flattened)
        private Map<String, Object> conditions;

        @Field(type = FieldType.Float)
        private Float confidence;

        public Object getConditionValue(String key) {
            if (conditions == null) {
                return null;
            }
            return conditions.get(key);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Observation {
        @Field(type = FieldType.Keyword)
        private String type;

        @Field(type = FieldType.Text, analyzer = "chemistry")
        private String content;

        @Field(type = FieldType.Float)
        private Float confidence;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChunkInfo {
        @Field(type = FieldType.Keyword)
        private String chunkId;

        @Field(type = FieldType.Text, analyzer = "chemistry")
        private String content;

        @Field(type = FieldType.Integer)
        private Integer orderNo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncMetadata {
        @Field(type = FieldType.Long)
        private Long version;

        // ✅ 关键修复：使用 String 类型存储日期，避免序列化问题
        // ES 中的 date 类型可以自动兼容 ISO 格式的字符串
        @Field(type = FieldType.Date)
        private String syncTime;

        @Field(type = FieldType.Keyword)
        private String dataHash;

        // 辅助方法：转换为 LocalDateTime
        public LocalDateTime getSyncTimeAsLocalDateTime() {
            if (syncTime == null) return null;
            return LocalDateTime.parse(syncTime.substring(0, 19)); // 截取到秒
        }
    }
}

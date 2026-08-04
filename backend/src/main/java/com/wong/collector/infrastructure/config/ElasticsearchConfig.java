package com.wong.collector.infrastructure.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    /**
     * 专门为 Elasticsearch 配置的 ObjectMapper（蛇形命名策略）
     * ✅ 修复：移除 @Primary，避免与 Spring Boot 默认 ObjectMapper 冲突
     */
    @Bean(name = "elasticsearchObjectMapper")
    public ObjectMapper elasticsearchObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // 设置蛇形命名策略（仅用于 ES 文档）
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        // 忽略 null 值，减少数据体积
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // 注册 JavaTimeModule 处理 JSR-310 日期类型
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        mapper.registerModule(javaTimeModule);

        // 禁用将日期写为时间戳，强制使用 ISO-8601 字符串格式
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 允许单引号（容错）
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

        // 允许反序列化时忽略未知字段（增强容错性）
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 允许基本类型为 null（避免 int 为 null 时报错）
        mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);

        return mapper;
    }

    /**
     * ✅ 修复：内部直接调用方法，不从 Spring 容器注入，避免 Bean 选择冲突
     */
    @Bean
    public ElasticsearchClient elasticsearchClient() {
        // 直接调用方法创建实例，不从 Spring 容器获取
        ObjectMapper esObjectMapper = elasticsearchObjectMapper();
        JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper(esObjectMapper);

        RestClient restClient = RestClient.builder(
                new HttpHost("localhost", 9200)).build();

        ElasticsearchTransport transport = new RestClientTransport(
                restClient, jsonpMapper);

        return new ElasticsearchClient(transport);
    }
}

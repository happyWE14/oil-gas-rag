package com.wong.collector.infrastructure.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchIndexInitializer {

    private final ElasticsearchClient esClient;
    private static final String INDEX_NAME = "materials";

    @PostConstruct
    public void init() {
        try {
            boolean exists = esClient.indices()
                    .exists(ExistsRequest.of(e -> e.index(INDEX_NAME)))
                    .value();

            if (!exists) {
                createIndex();
            } else {
                log.info("ES index '{}' already exists", INDEX_NAME);
            }
        } catch (IOException e) {
            log.error("Failed to check/create ES index", e);
        }
    }

    private void createIndex() throws IOException {
        String mappingJson = """
            {
              "settings": {
                "index": {
                  "number_of_shards": 1,
                  "number_of_replicas": 0,
                  "refresh_interval": "5s",
                  "max_result_window": 50000
                },
                "analysis": {
                  "analyzer": {
                    "chemistry": {
                      "type": "custom",
                      "tokenizer": "ik_max_word",
                      "filter": ["lowercase", "asciifolding"]
                    }
                  }
                }
              },
              "mappings": {
                "dynamic": "strict",
                "properties": {
                  "doc_id": { "type": "keyword" },
                  "paper": {
                    "properties": {
                      "paper_id": { "type": "keyword" },
                      "title": { 
                        "type": "text", 
                        "analyzer": "chemistry",
                        "fields": { "keyword": { "type": "keyword" } }
                      },
                      "authors": { "type": "text", "analyzer": "chemistry" },
                      "year": { "type": "integer" },
                      "doi": { "type": "keyword" },
                      "abstract_content": { "type": "text", "analyzer": "chemistry" }
                    }
                  },
                  "material": {
                    "properties": {
                      "material_key": { "type": "keyword" },
                      "material_name": { 
                        "type": "text", 
                        "analyzer": "chemistry",
                        "copy_to": ["full_text_search"]
                      },
                      "confidence": { "type": "float" }
                    }
                  },
                  "metrics": {
                    "type": "nested",
                    "properties": {
                      "metric_key": { "type": "keyword" },
                      "metric_name": { "type": "keyword" },
                      "value_type": { "type": "keyword" },
                      "value_num": { "type": "double" },
                      "value_min": { "type": "double" },
                      "value_max": { "type": "double" },
                      "value_text": { "type": "text" },
                      "unit": { "type": "keyword" },
                      "conditions": { 
                        "type": "flattened",
                        "ignore_above": 256
                      },
                      "confidence": { "type": "float" }
                    }
                  },
                  "observations": {
                    "type": "nested",
                    "properties": {
                      "type": { "type": "keyword" },
                      "content": { "type": "text", "analyzer": "chemistry" },
                      "confidence": { "type": "float" }
                    }
                  },
                  "chunks": {
                    "type": "nested",
                    "properties": {
                      "chunk_id": { "type": "keyword" },
                      "content": { "type": "text", "analyzer": "chemistry" },
                      "order_no": { "type": "integer" }
                    }
                  },
                  "embedding_vector": {
                    "type": "dense_vector",
                    "dims": 2048,
                    "index": true,
                    "similarity": "cosine"
                  },
                  "full_text_search": { 
                    "type": "text", 
                    "analyzer": "chemistry" 
                  },
                  "sync_metadata": {
                    "properties": {
                      "version": { "type": "long" },
                      "sync_time": { "type": "date" },
                      "data_hash": { "type": "keyword" }
                    }
                  }
                }
              }
            }
            """;

        esClient.indices().create(CreateIndexRequest.of(c -> c
                .index(INDEX_NAME)
                .withJson(new StringReader(mappingJson))
        ));

        log.info("Successfully created ES index '{}'", INDEX_NAME);
    }
}

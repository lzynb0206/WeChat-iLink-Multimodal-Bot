package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagConfig {
    private boolean enabled = true;
    private String knowledgeBase = "classpath:rag/knowledge-base.json";
    private int maxResults = 3;
}

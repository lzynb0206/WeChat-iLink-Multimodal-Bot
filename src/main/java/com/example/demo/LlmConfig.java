package com.example.demo;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "llm")
@Data
public class LlmConfig {
    private String apiUrl;
    private String apiKey;
    private String model = "gpt-4o-mini";
}

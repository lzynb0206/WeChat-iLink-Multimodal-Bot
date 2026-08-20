package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "audio")
public class AudioConfig {
    private String nodeExecutable = "node";
    private String silkDecoderScript = "scripts/decode-silk.mjs";
    private int sampleRate = 24_000;
    private long decodeTimeoutSeconds = 30;
}

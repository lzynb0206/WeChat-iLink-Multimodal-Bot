package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "dashscope")
public class AiConfig {
    private String apiKey;
    private String compatibleApiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private String apiUrl = "https://dashscope.aliyuncs.com/api/v1";
    private String chatModel = "qwen-flash";
    private String intentModel = "qwen-flash";
    private String searchModel = "qwen-plus";
    private String translationModel = "qwen-mt-flash";
    private String visionModel = "qwen3-vl-flash";
    private String imageModel = "qwen-image-2.0";
    private String asrModel = "qwen3-asr-flash";
    private String ttsModel = "cosyvoice-v3-flash";
    private String ttsVoice = "longanyang";
}

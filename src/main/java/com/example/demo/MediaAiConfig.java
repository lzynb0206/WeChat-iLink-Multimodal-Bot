package com.example.demo;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "dashscope")
public class MediaAiConfig {
    private String apiKey;
    private String compatibleApiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private String apiUrl = "https://dashscope.aliyuncs.com/api/v1";
    private String visionModel = "qwen3-vl-flash";
    private String imageModel = "qwen-image-2.0";
    private String asrModel = "qwen3-asr-flash";
    private String ttsModel = "cosyvoice-v3.5-flash";
    private String ttsVoice = "longanyang";
    private String silkCodecPath = "tools/macos-arm64/silk_codec";
}

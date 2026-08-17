// 这个包专门存放项目配置类。
package com.example.demo.config;

// Lombok 的 @Data 自动生成 getter 和 setter。
import lombok.Data;
// 用于把 YAML 配置绑定到 Java 对象。
import org.springframework.boot.context.properties.ConfigurationProperties;
// 让 Spring 自动管理这个配置对象。
import org.springframework.stereotype.Component;

// 自动生成配置字段的 getter 和 setter。
@Data
// 把该类注册到 Spring 容器。
@Component
// 读取 application.yaml 中以 dashscope 开头的所有配置。
@ConfigurationProperties(prefix = "dashscope")
public class MediaAiConfig {
    // 阿里云百炼 API Key，图片和语音模型共用这个 Key。
    private String apiKey;
    // OpenAI 兼容接口，用于图片理解和语音识别。
    private String compatibleApiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    // 百炼原生 API 根地址，用于图片生成和语音合成。
    private String apiUrl = "https://dashscope.aliyuncs.com/api/v1";
    // 接收图片后理解图片内容的模型。
    private String visionModel = "qwen3-vl-flash";
    // 根据文字生成图片的模型。
    private String imageModel = "qwen-image-2.0";
    // 把 WAV 语音转成文字的模型。
    private String asrModel = "qwen3-asr-flash";
    // 把回答文字合成为 WAV 语音的模型。
    private String ttsModel = "cosyvoice-v3.5-flash";
    // CosyVoice 使用的发音人名称。
    private String ttsVoice = "longanyang";
    // 当前 Apple Silicon Mac 使用的 SILK 编解码程序路径。
    private String silkCodecPath = "tools/macos-arm64/silk_codec";
}

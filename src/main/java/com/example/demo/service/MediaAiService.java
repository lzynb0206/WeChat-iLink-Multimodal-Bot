// service 包保存与阿里云百炼交互的业务代码。
package com.example.demo.service;

// 导入阿里云百炼的配置对象。
import com.example.demo.config.MediaAiConfig;
// JsonNode 用来按路径读取百炼返回的 JSON。
import com.fasterxml.jackson.databind.JsonNode;
// ObjectMapper 用来解析 JSON 字符串。
import com.fasterxml.jackson.databind.ObjectMapper;
// HttpEntity 用来组合 HTTP 请求头和请求正文。
import org.springframework.http.HttpEntity;
// HttpHeaders 用来设置鉴权信息和正文类型。
import org.springframework.http.HttpHeaders;
// MediaType 提供 application/json 常量。
import org.springframework.http.MediaType;
// ResponseEntity 保存接口返回的完整 HTTP 响应。
import org.springframework.http.ResponseEntity;
// @Service 让 Spring 创建并管理这个类。
import org.springframework.stereotype.Service;
// StringUtils 用来判断字符串是否真正包含文字。
import org.springframework.util.StringUtils;
// RestTemplate 用来调用阿里云 HTTP 接口和下载结果文件。
import org.springframework.web.client.RestTemplate;

// URI 用来把图片或音频下载地址转换成标准地址对象。
import java.net.URI;
// Base64 用来把图片和 WAV 二进制转成接口能接收的文本。
import java.util.Base64;
// List 用来创建 JSON 数组。
import java.util.List;
// Map 用来创建 JSON 对象。
import java.util.Map;

// 把这个类注册为 Spring 的业务服务对象。
@Service
public class MediaAiService {
    // 保存百炼 Key、接口地址和所有模型名称。
    private final MediaAiConfig config;
    // 负责读取接口返回的 JSON。
    private final ObjectMapper objectMapper;
    // 负责发送 HTTP 请求。
    private final RestTemplate restTemplate;

    // Spring 自动把 MediaAiConfig 传入构造方法。
    public MediaAiService(MediaAiConfig config) {
        // 保存配置对象。
        this.config = config;
        // 创建 JSON 解析工具。
        this.objectMapper = new ObjectMapper();
        // 创建 HTTP 客户端。
        this.restTemplate = new RestTemplate();
    }

    // 判断用户是否已经填写阿里云百炼 API Key。
    public boolean isConfigured() {
        // Key 不是 null、空字符串或全空格时返回 true。
        return StringUtils.hasText(config.getApiKey());
    }

    // 把收到的图片交给千问视觉模型理解，并返回中文说明。
    public String understandImage(byte[] imageBytes, String mimeType) {
        // 调用模型之前先检查 Key。
        requireApiKey();
        // 把图片字节转换成 data:image/jpeg;base64,... 格式。
        String dataUrl = "data:" + mimeType + ";base64,"
                + Base64.getEncoder().encodeToString(imageBytes);
        // 创建符合 OpenAI 兼容协议的 JSON 请求正文。
        Map<String, Object> body = Map.of(
                // 指定图片理解模型。
                "model", config.getVisionModel(),
                // 创建只有一条用户消息的数组。
                "messages", List.of(Map.of(
                        // 表明这条消息来自用户。
                        "role", "user",
                        // 一条消息里同时放图片和文字指令。
                        "content", List.of(
                                // 把 Base64 图片作为 image_url 内容发送。
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)),
                                // 告诉模型如何回答。
                                Map.of("type", "text", "text",
                                        "请用中文简洁描述图片内容；如果图片里有文字，也请提取主要文字。")
                        )
                ))
        );
        // 调用百炼兼容接口并取得 JSON 响应。
        JsonNode root = postJson(config.getCompatibleApiUrl(), body);
        // 取出模型回答文字。
        String answer = root.at("/choices/0/message/content").asText();
        // 如果回答为空，说明接口响应结构异常或调用失败。
        if (!StringUtils.hasText(answer)) {
            // 抛出异常，由微信消息处理层捕获并提示用户。
            throw new IllegalStateException("千问图片理解未返回内容");
        }
        // 返回图片理解结果。
        return answer;
    }

    // 根据用户的文字描述生成一张图片，并返回图片二进制。
    public byte[] generateImage(String prompt) {
        // 调用模型之前先检查 Key。
        requireApiKey();
        // 构造千问生图接口需要的 JSON 请求正文。
        Map<String, Object> body = Map.of(
                // 指定文字生成图片模型。
                "model", config.getImageModel(),
                // 把用户提示词放进 messages 数组。
                "input", Map.of("messages", List.of(Map.of(
                        // 表明这条消息来自用户。
                        "role", "user",
                        // content 数组里放入文字提示词。
                        "content", List.of(Map.of("text", prompt))
                ))),
                // 生成一张正方形图片，并允许模型扩写简单提示词。
                "parameters", Map.of("size", "1024*1024", "n", 1, "prompt_extend", true)
        );
        // 调用百炼原生多模态生成接口。
        JsonNode root = postJson(config.getApiUrl()
                + "/services/aigc/multimodal-generation/generation", body);
        // 优先从千问图像接口的标准位置读取图片 URL。
        String imageUrl = root.at("/output/choices/0/message/content/0/image").asText();
        // 某些模型版本会把 URL 放在 results 数组中，因此增加兼容读取。
        if (!StringUtils.hasText(imageUrl)) {
            // 从备用 JSON 路径读取图片 URL。
            imageUrl = root.at("/output/results/0/url").asText();
        }
        // 两个位置都没有 URL 时，接口没有正常生成图片。
        if (!StringUtils.hasText(imageUrl)) {
            // 抛出包含响应内容的异常，便于开发阶段查看问题。
            throw new IllegalStateException("千问生图未返回图片地址：" + root);
        }
        // 图片 URL 只有短期有效，因此立即下载成 byte[] 再发送给微信。
        return restTemplate.getForObject(URI.create(imageUrl), byte[].class);
    }

    // 把已经从 SILK 转成 WAV 的语音识别为文字。
    public String transcribeAudio(byte[] wavBytes) {
        // 调用模型之前先检查 Key。
        requireApiKey();
        // 把 WAV 字节转换成 Base64 data URL，因此不需要上传公网文件。
        String dataUrl = "data:audio/wav;base64,"
                + Base64.getEncoder().encodeToString(wavBytes);
        // 创建 Qwen ASR 的 OpenAI 兼容请求正文。
        Map<String, Object> body = Map.of(
                // 指定语音识别模型。
                "model", config.getAsrModel(),
                // messages 数组里只发送这一段音频。
                "messages", List.of(Map.of(
                        // 表明内容来自用户。
                        "role", "user",
                        // content 数组中放 input_audio 类型内容。
                        "content", List.of(Map.of(
                                // 固定类型 input_audio 表示输入是音频。
                                "type", "input_audio",
                                // data 字段保存 Base64 WAV。
                                "input_audio", Map.of("data", dataUrl)
                        ))
                )),
                // 指定中文识别，并开启数字等文本格式整理。
                "asr_options", Map.of("language", "zh", "enable_itn", true)
        );
        // 调用百炼兼容接口。
        JsonNode root = postJson(config.getCompatibleApiUrl(), body);
        // 读取识别出的文字。
        String text = root.at("/choices/0/message/content").asText();
        // 如果没有识别文字，就抛出异常。
        if (!StringUtils.hasText(text)) {
            // 带上原始响应便于排查模型错误。
            throw new IllegalStateException("语音识别未返回文字：" + root);
        }
        // 返回识别结果，下一步会交给 DeepSeek。
        return text;
    }

    // 使用 CosyVoice 把回答文字合成为 WAV 音频。
    public byte[] synthesizeSpeech(String text) {
        // 调用模型之前先检查 Key。
        requireApiKey();
        // 构造 CosyVoice 请求正文。
        Map<String, Object> body = Map.of(
                // 指定文字转语音模型。
                "model", config.getTtsModel(),
                // input 保存要朗读的文字和音频参数。
                "input", Map.of(
                        // 要朗读的 DeepSeek 回答。
                        "text", text,
                        // 使用配置文件中的发音人。
                        "voice", config.getTtsVoice(),
                        // 要求返回 WAV 格式，便于继续转换为 SILK。
                        "format", "wav",
                        // 微信 SILK 使用 24kHz 采样率。
                        "sample_rate", 24000
                )
        );
        // 调用 CosyVoice 接口。
        JsonNode root = postJson(config.getApiUrl()
                + "/services/audio/tts/SpeechSynthesizer", body);
        // 优先从新版响应位置读取音频 URL。
        String audioUrl = root.at("/output/audio/url").asText();
        // 兼容可能返回 audio_url 的旧版响应。
        if (!StringUtils.hasText(audioUrl)) {
            // 从备用位置读取音频 URL。
            audioUrl = root.at("/output/audio_url").asText();
        }
        // 没有 URL 表示语音合成没有成功。
        if (!StringUtils.hasText(audioUrl)) {
            // 抛出异常交给上层处理。
            throw new IllegalStateException("CosyVoice 未返回音频地址");
        }
        // 立即下载 WAV 音频并保存在内存 byte[] 中。
        return restTemplate.getForObject(URI.create(audioUrl), byte[].class);
    }

    // 统一发送带百炼鉴权信息的 JSON POST 请求。
    private JsonNode postJson(String url, Map<String, Object> body) {
        // 创建包含 API Key 的请求头。
        HttpHeaders headers = authHeaders();
        // 把请求正文和请求头组合起来并发送 POST 请求。
        ResponseEntity<String> response = restTemplate.postForEntity(
                url, new HttpEntity<>(body, headers), String.class);
        // 把响应正文解析为 JsonNode 后返回。
        return readJson(response.getBody());
    }

    // 创建每个百炼请求都需要的 HTTP 请求头。
    private HttpHeaders authHeaders() {
        // 创建一个空请求头对象。
        HttpHeaders headers = new HttpHeaders();
        // 指定请求正文格式为 JSON。
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 把百炼 API Key 作为 Bearer Token 写入 Authorization。
        headers.setBearerAuth(config.getApiKey());
        // 返回准备好的请求头。
        return headers;
    }

    // 把 JSON 字符串转换成方便读取的树形对象。
    private JsonNode readJson(String json) {
        // JSON 可能不合法，因此使用 try/catch。
        try {
            // 解析并返回 JSON。
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            // 包装成业务异常，让上层统一处理。
            throw new IllegalStateException("无法解析百炼响应", exception);
        }
    }

    // 在调用任何百炼模型前检查 API Key。
    private void requireApiKey() {
        // 没有 Key 时不能请求百炼接口。
        if (!isConfigured()) {
            // 抛出包含配置方法的提示。
            throw new IllegalStateException("请先配置 DASHSCOPE_API_KEY");
        }
    }
}

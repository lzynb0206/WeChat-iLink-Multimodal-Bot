// service 包负责项目的具体业务逻辑。
package com.example.demo.service;

// 导入 DeepSeek 配置对象。
import com.example.demo.config.LlmConfig;
// JsonNode 用于读取 DeepSeek 返回的 JSON。
import com.fasterxml.jackson.databind.JsonNode;
// ObjectMapper 用于在 Java 对象和 JSON 之间转换。
import com.fasterxml.jackson.databind.ObjectMapper;
// HttpEntity 把请求头和请求正文组合成一次 HTTP 请求。
import org.springframework.http.HttpEntity;
// HttpHeaders 用于设置 Content-Type 和 Authorization。
import org.springframework.http.HttpHeaders;
// MediaType 提供 application/json 常量。
import org.springframework.http.MediaType;
// ResponseEntity 保存 HTTP 状态、响应头和响应正文。
import org.springframework.http.ResponseEntity;
// @Service 让 Spring 管理这个业务类。
import org.springframework.stereotype.Service;
// StringUtils 用于安全判断字符串是否为空。
import org.springframework.util.StringUtils;
// RestTemplate 用于发送 HTTP 请求。
import org.springframework.web.client.RestTemplate;

// List 用来创建 messages 数组。
import java.util.List;
// Map 用来创建 JSON 对象结构。
import java.util.Map;

// 把 LlmService 注册为 Spring Bean。
@Service
public class LlmService {

    // 保存 DeepSeek 的地址、Key 和模型名。
    private final LlmConfig llmConfig;
    // 负责向 DeepSeek 发送 HTTP 请求。
    private final RestTemplate restTemplate;
    // 负责生成请求 JSON 和解析响应 JSON。
    private final ObjectMapper objectMapper;

    // Spring 会自动把 LlmConfig 传入这个构造方法。
    public LlmService(LlmConfig llmConfig) {
        // 保存配置对象，供 chat 方法使用。
        this.llmConfig = llmConfig;
        // 创建 HTTP 客户端。
        this.restTemplate = new RestTemplate();
        // 创建 JSON 处理对象。
        this.objectMapper = new ObjectMapper();
    }

    // 把用户文字发送给 DeepSeek，并返回模型回答。
    public String chat(String userPrompt) {
        // 如果没有填写 Key，就直接提示用户配置，不发送网络请求。
        if (!StringUtils.hasText(llmConfig.getApiKey())) {
            // 返回适合显示在微信里的提示文字。
            return "DeepSeek 尚未配置，请先设置 DEEPSEEK_API_KEY。";
        }
        // 网络和 JSON 操作都可能失败，因此使用 try/catch 保护机器人线程。
        try {
            // 创建 HTTP 请求头。
            HttpHeaders headers = new HttpHeaders();
            // 告诉 DeepSeek 请求正文是 JSON。
            headers.setContentType(MediaType.APPLICATION_JSON);
            // 使用 Bearer 方式把 API Key 放入 Authorization 请求头。
            headers.setBearerAuth(llmConfig.getApiKey());

            // 按 Chat Completions 协议组织请求正文。
            Map<String, Object> body = Map.of(
                    // 指定使用的 DeepSeek 模型。
                    "model", llmConfig.getModel(),
                    // messages 是发送给模型的对话消息数组。
                    "messages", List.of(
                            // role=user 表示这句话来自用户。
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    // temperature 控制回答随机程度，数值越大越有创造性。
                    "temperature", 0.7
            );

            // 把 Java Map 转成 JSON 字符串，并与请求头组合起来。
            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            // 向 DeepSeek 地址发送 POST 请求，并把响应正文作为字符串接收。
            ResponseEntity<String> response = restTemplate.postForEntity(
                    llmConfig.getApiUrl(), request, String.class);

            // 把 DeepSeek 返回的 JSON 字符串解析成树形对象。
            JsonNode root = objectMapper.readTree(response.getBody());
            // 按 JSON 路径取出第一个回答的正文并返回。
            return root.at("/choices/0/message/content").asText();
        } catch (Exception exception) {
            // 发生超时、鉴权失败或解析失败时，给微信返回统一提示。
            return "大模型暂时无法响应，请稍后再试。";
        }
    }
}

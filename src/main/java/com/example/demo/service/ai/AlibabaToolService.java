package com.example.demo.service.ai;

import com.example.demo.config.AiConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AlibabaToolService {
    private final AiConfig config;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public AlibabaToolService(AiConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.restTemplate = new RestTemplate();
    }

    public String searchNews(String query, int limit) {
        requireApiKey();
        String prompt = """
                请联网搜索与“%s”相关的最新新闻，最多返回%d条。
                每条必须包含标题、事件或发布时间、简短摘要、来源名称和可访问的原始URL。
                按时间从新到旧排列；无法确认的信息不要编造，并明确检索时间为%s。
                """.formatted(query, limit, OffsetDateTime.now());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getSearchModel());
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        body.put("enable_search", true);
        body.put("enable_thinking", false);
        body.put("temperature", 0.1);
        return completion(body);
    }

    public String translate(String text, String sourceLanguage, String targetLanguage) {
        requireApiKey();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getTranslationModel());
        body.put("messages", List.of(Map.of("role", "user", "content", text)));
        body.put("translation_options", Map.of(
                "source_lang", sourceLanguage,
                "target_lang", targetLanguage));
        return completion(body);
    }

    private String completion(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());
        String response = restTemplate.postForObject(
                config.getCompatibleApiUrl(), new HttpEntity<>(body, headers), String.class);
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.at("/choices/0/message/content").asText();
            if (!StringUtils.hasText(content)) {
                throw new IllegalStateException("百炼工具模型未返回内容：" + root);
            }
            return content.trim();
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("无法解析百炼工具模型响应", exception);
        }
    }

    private void requireApiKey() {
        if (!StringUtils.hasText(config.getApiKey())) {
            throw new IllegalStateException("请先配置 DASHSCOPE_API_KEY");
        }
    }
}

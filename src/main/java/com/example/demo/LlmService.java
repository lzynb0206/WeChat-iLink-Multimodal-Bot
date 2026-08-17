package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
public class LlmService {

    private final LlmConfig llmConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public LlmService(LlmConfig llmConfig) {
        this.llmConfig = llmConfig;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 发送prompt，返回AI回答文本
     */
    public String chat(String userPrompt) {
        if (!StringUtils.hasText(llmConfig.getApiKey())) {
            return "DeepSeek 尚未配置，请先设置 DEEPSEEK_API_KEY。";
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(llmConfig.getApiKey());

            Map<String, Object> body = Map.of(
                    "model", llmConfig.getModel(),
                    "messages", List.of(
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", 0.7
            );

            HttpEntity<String> req = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(llmConfig.getApiUrl(), req, String.class);

            JsonNode root = objectMapper.readTree(resp.getBody());
            return root.at("/choices/0/message/content").asText();

        } catch (Exception e) {
            return "大模型暂时无法响应，请稍后再试。";
        }
    }
}

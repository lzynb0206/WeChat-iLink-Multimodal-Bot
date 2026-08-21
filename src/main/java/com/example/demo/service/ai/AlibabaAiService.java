package com.example.demo.service.ai;

import com.example.demo.config.AiConfig;
import com.example.demo.model.ActionType;
import com.example.demo.model.IntentResult;
import com.example.demo.model.ReplyMode;
import com.example.demo.tool.ToolCallingEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class AlibabaAiService {
    private final AiConfig config;
    private final ToolCallingEngine toolCallingEngine;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public AlibabaAiService(AiConfig config, ToolCallingEngine toolCallingEngine) {
        this.config = config;
        this.toolCallingEngine = toolCallingEngine;
        this.objectMapper = new ObjectMapper();
        this.restTemplate = new RestTemplate();
    }

    public boolean isConfigured() {
        return StringUtils.hasText(config.getApiKey());
    }

    public IntentResult recognizeIntent(String text, ReplyMode defaultReplyMode) {
        String systemPrompt = """
                你是微信机器人的意图分类器。请只输出JSON对象，不要解释。
                JSON字段必须包含：action、replyMode、content、location。
                action只能是CHAT、IMAGE_GENERATION、WEATHER。
                replyMode只能是TEXT、VOICE。
                用户要求画图、生成图片时使用IMAGE_GENERATION，并把绘图描述放入content。
                用户询问天气、温度、是否下雨时使用WEATHER，并把最具体、可独立查询的城市、区县名称放入location。
                不要把上级城市和下级城市连在一起，例如“苏州张家港”应返回“张家港”，
                “江苏省苏州市张家港市”也应返回“张家港”，“上海浦东新区”应返回“浦东新区”。
                其他情况使用CHAT，并把真正的问题放入content。
                用户明确要求语音、朗读、说出来时replyMode为VOICE；明确要求文字时为TEXT；
                没有明确要求时replyMode使用%s。
                location不存在时返回空字符串，不要猜测用户位置。
                """.formatted(defaultReplyMode.name());
        Map<String, Object> body = Map.of(
                "model", config.getIntentModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", text)
                ),
                "response_format", Map.of("type", "json_object"),
                "enable_thinking", false,
                "temperature", 0.1
        );

        try {
            requireApiKey();
            String content = chatCompletion(body);
            JsonNode result = objectMapper.readTree(content);
            ActionType action = enumValue(ActionType.class, result.path("action").asText(), ActionType.CHAT);
            ReplyMode replyMode = enumValue(
                    ReplyMode.class, result.path("replyMode").asText(), defaultReplyMode);
            String normalizedContent = result.path("content").asText(text).trim();
            String location = result.path("location").asText("").trim();
            return new IntentResult(action, replyMode, normalizedContent, location);
        } catch (Exception exception) {
            log.warn("千问意图识别失败，使用本地规则兜底", exception);
            return fallbackIntent(text, defaultReplyMode);
        }
    }

    public String chatWithTools(String userPrompt) {
        requireApiKey();
        List<Map<String, Object>> messages = List.of(
                Map.of(
                        "role", "system",
                                "content", """
                                你是微信助手。需要实时天气、最新新闻、翻译、精确计算或温度换算时必须使用提供的工具。
                                一句话包含多个互不依赖的任务时，在同一轮返回多个tool_calls，程序会并行执行它们。
                                如果后一步依赖前一步结果，一轮只调用当前可执行的工具，取得真实结果后下一轮再调用后续工具，不能猜测参数。
                                例如同时查询两个城市天气时可以并行；查询天气并换算华氏度时，必须先查询天气，再使用其temperature_celsius换算。
                                查询新闻时要保留工具返回的时间、数据来源和链接；翻译时不要擅自改写原意。
                                最终请用中文简洁回答并说明数据来源。
                                """
                ),
                Map.of("role", "user", "content", userPrompt)
        );
        return toolCallingEngine.run(messages, currentMessages -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", config.getChatModel());
            body.put("messages", currentMessages);
            body.put("tools", toolCallingEngine.toolDefinitions());
            body.put("tool_choice", "auto");
            body.put("parallel_tool_calls", true);
            body.put("enable_thinking", false);
            body.put("temperature", 0.3);
            JsonNode root = postJson(config.getCompatibleApiUrl(), body);
            JsonNode message = root.at("/choices/0/message");
            if (message.isMissingNode()) {
                throw new IllegalStateException("千问未返回消息：" + root);
            }
            return message;
        });
    }

    public String understandImage(byte[] imageBytes, String mimeType) {
        requireApiKey();
        String dataUrl = "data:" + mimeType + ";base64,"
                + Base64.getEncoder().encodeToString(imageBytes);
        Map<String, Object> body = Map.of(
                "model", config.getVisionModel(),
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)),
                                Map.of("type", "text", "text",
                                        "请用中文简洁描述图片内容；如果有文字，请提取主要文字。")
                        )
                ))
        );
        return chatCompletion(body);
    }

    public byte[] generateImage(String prompt) {
        requireApiKey();
        Map<String, Object> body = Map.of(
                "model", config.getImageModel(),
                "input", Map.of("messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(Map.of("text", prompt))
                ))),
                "parameters", Map.of("size", "1024*1024", "n", 1, "prompt_extend", true)
        );
        JsonNode root = postJson(
                config.getApiUrl() + "/services/aigc/multimodal-generation/generation", body);
        String imageUrl = firstText(
                root.at("/output/choices/0/message/content/0/image"),
                root.at("/output/results/0/url"));
        if (!StringUtils.hasText(imageUrl)) {
            throw new IllegalStateException("千问生图未返回图片地址：" + root);
        }
        return download(imageUrl, "图片");
    }

    public String transcribeAudio(byte[] wavBytes) {
        requireApiKey();
        String dataUrl = "data:audio/wav;base64,"
                + Base64.getEncoder().encodeToString(wavBytes);
        Map<String, Object> body = Map.of(
                "model", config.getAsrModel(),
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(Map.of(
                                "type", "input_audio",
                                "input_audio", Map.of("data", dataUrl)
                        ))
                )),
                "stream", false,
                "asr_options", Map.of("language", "zh", "enable_itn", true)
        );
        return chatCompletion(body);
    }

    public byte[] synthesizeSpeech(String text) {
        requireApiKey();
        Map<String, Object> body = Map.of(
                "model", config.getTtsModel(),
                "input", Map.of(
                        "text", text,
                        "voice", config.getTtsVoice(),
                        "format", "wav",
                        "sample_rate", 24_000
                )
        );
        JsonNode root = postJson(
                config.getApiUrl() + "/services/audio/tts/SpeechSynthesizer", body);
        String audioUrl = firstText(
                root.at("/output/audio/url"),
                root.at("/output/audio_url"));
        if (!StringUtils.hasText(audioUrl)) {
            throw new IllegalStateException("语音合成未返回音频地址：" + root);
        }
        return download(audioUrl, "语音");
    }

    private IntentResult fallbackIntent(String text, ReplyMode defaultReplyMode) {
        String lower = text.toLowerCase(Locale.ROOT);
        ReplyMode replyMode = lower.contains("语音") || lower.contains("朗读")
                || lower.contains("说出来") || lower.contains("读出来")
                ? ReplyMode.VOICE
                : defaultReplyMode;
        if (lower.contains("文字回复") || lower.contains("用文字") || lower.contains("打字")) {
            replyMode = ReplyMode.TEXT;
        }
        if (lower.contains("图片") || lower.contains("生成一张")
                || lower.contains("画一张") || lower.contains("帮我画")) {
            String content = text.replaceFirst(
                    "^(请|帮我)?(生成|画)(一张)?(图片)?[:：]?", "").trim();
            return new IntentResult(ActionType.IMAGE_GENERATION, ReplyMode.TEXT,
                    content.isEmpty() ? "一只可爱的小猫" : content, "");
        }
        if (lower.contains("天气") || lower.contains("气温") || lower.contains("温度")
                || lower.contains("下雨")) {
            String location = text.replaceAll(
                    "(请|帮我|查询|查一下|告诉我|用语音|语音|今天|现在|当前|的|天气|气温|温度|怎么样|如何|是否|会不会|下雨|[?？])",
                    "").trim();
            return new IntentResult(ActionType.WEATHER, replyMode, text, location);
        }
        return new IntentResult(ActionType.CHAT, replyMode, text, "");
    }

    private String chatCompletion(Map<String, Object> body) {
        JsonNode root = postJson(config.getCompatibleApiUrl(), body);
        String content = root.at("/choices/0/message/content").asText();
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("千问未返回内容：" + root);
        }
        return content.trim();
    }

    private JsonNode postJson(String url, Map<String, Object> body) {
        ResponseEntity<String> response = restTemplate.postForEntity(
                url, new HttpEntity<>(body, authHeaders()), String.class);
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception exception) {
            throw new IllegalStateException("无法解析阿里云百炼响应", exception);
        }
    }

    private byte[] download(String url, String resourceName) {
        byte[] bytes = restTemplate.getForObject(URI.create(url), byte[].class);
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException(resourceName + "下载结果为空");
        }
        return bytes;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());
        return headers;
    }

    private String firstText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && StringUtils.hasText(node.asText())) {
                return node.asText();
            }
        }
        return "";
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void requireApiKey() {
        if (!isConfigured()) {
            throw new IllegalStateException("请先配置 DASHSCOPE_API_KEY");
        }
    }
}

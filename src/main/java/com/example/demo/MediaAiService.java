package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class MediaAiService {
    private final MediaAiConfig config;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public MediaAiService(MediaAiConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
    }

    public boolean isConfigured() {
        return StringUtils.hasText(config.getApiKey());
    }

    public String understandImage(byte[] imageBytes, String mimeType) {
        requireApiKey();
        String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
        Map<String, Object> body = Map.of(
                "model", config.getVisionModel(),
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)),
                                Map.of("type", "text", "text", "请用中文简洁描述图片内容；如果图片里有文字，也请提取主要文字。")
                        )
                ))
        );
        JsonNode root = postJson(config.getCompatibleApiUrl(), body, false);
        String answer = root.at("/choices/0/message/content").asText();
        if (!StringUtils.hasText(answer)) {
            throw new IllegalStateException("千问图片理解未返回内容");
        }
        return answer;
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
        JsonNode root = postJson(config.getApiUrl()
                + "/services/aigc/multimodal-generation/generation", body, false);
        String imageUrl = root.at("/output/choices/0/message/content/0/image").asText();
        if (!StringUtils.hasText(imageUrl)) {
            imageUrl = root.at("/output/results/0/url").asText();
        }
        if (!StringUtils.hasText(imageUrl)) {
            throw new IllegalStateException("千问生图未返回图片地址：" + root);
        }
        return restTemplate.getForObject(URI.create(imageUrl), byte[].class);
    }

    public String transcribeAudio(byte[] wavBytes) {
        requireApiKey();
        String dataUrl = "data:audio/wav;base64," + Base64.getEncoder().encodeToString(wavBytes);
        Map<String, Object> body = Map.of(
                "model", config.getAsrModel(),
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(Map.of(
                                "type", "input_audio",
                                "input_audio", Map.of("data", dataUrl)
                        ))
                )),
                "asr_options", Map.of("language", "zh", "enable_itn", true)
        );
        JsonNode root = postJson(config.getCompatibleApiUrl(), body, false);
        String text = root.at("/choices/0/message/content").asText();
        if (!StringUtils.hasText(text)) {
            throw new IllegalStateException("语音识别未返回文字：" + root);
        }
        return text;
    }

    /**
     * Paraformer-v2 仅接受公网 HTTP/HTTPS 音频 URL。
     */
    public String transcribePublicAudio(String publicAudioUrl) throws InterruptedException {
        requireApiKey();
        Map<String, Object> body = Map.of(
                "model", config.getAsrModel(),
                "input", Map.of("file_urls", List.of(publicAudioUrl)),
                "parameters", Map.of("language_hints", List.of("zh", "en"))
        );
        JsonNode submitted = postJson(config.getApiUrl() + "/services/audio/asr/transcription", body, true);
        String taskId = submitted.at("/output/task_id").asText();
        if (!StringUtils.hasText(taskId)) {
            throw new IllegalStateException("Paraformer 未返回 task_id");
        }

        JsonNode result = null;
        for (int i = 0; i < 60; i++) {
            result = getJson(config.getApiUrl() + "/tasks/" + taskId);
            String status = result.at("/output/task_status").asText();
            if ("SUCCEEDED".equals(status)) {
                break;
            }
            if ("FAILED".equals(status) || "CANCELED".equals(status)) {
                throw new IllegalStateException("Paraformer 识别失败：" + result.at("/message").asText());
            }
            Thread.sleep(1000);
        }
        String transcriptionUrl = result == null ? "" : result.at("/output/results/0/transcription_url").asText();
        if (!StringUtils.hasText(transcriptionUrl)) {
            throw new IllegalStateException("Paraformer 识别超时或没有返回结果");
        }
        JsonNode transcript = getJsonWithoutAuth(transcriptionUrl);
        return transcript.at("/transcripts/0/text").asText();
    }

    public byte[] synthesizeSpeech(String text) {
        requireApiKey();
        Map<String, Object> body = Map.of(
                "model", config.getTtsModel(),
                "input", Map.of(
                        "text", text,
                        "voice", config.getTtsVoice(),
                        "format", "wav",
                        "sample_rate", 24000
                )
        );
        JsonNode root = postJson(config.getApiUrl() + "/services/audio/tts/SpeechSynthesizer", body, false);
        String audioUrl = root.at("/output/audio/url").asText();
        if (!StringUtils.hasText(audioUrl)) {
            audioUrl = root.at("/output/audio_url").asText();
        }
        if (!StringUtils.hasText(audioUrl)) {
            throw new IllegalStateException("CosyVoice 未返回音频地址");
        }
        return restTemplate.getForObject(URI.create(audioUrl), byte[].class);
    }

    private JsonNode postJson(String url, Map<String, Object> body, boolean async) {
        HttpHeaders headers = authHeaders();
        if (async) {
            headers.set("X-DashScope-Async", "enable");
        }
        ResponseEntity<String> response = restTemplate.postForEntity(
                url, new HttpEntity<>(body, headers), String.class);
        return readJson(response.getBody());
    }

    private JsonNode getJson(String url) {
        String body = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class).getBody();
        return body == null ? objectMapper.createObjectNode() : readJson(body);
    }

    private JsonNode getJsonWithoutAuth(String url) {
        return readJson(restTemplate.getForObject(url, String.class));
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());
        return headers;
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("无法解析百炼响应", e);
        }
    }

    private void requireApiKey() {
        if (!isConfigured()) {
            throw new IllegalStateException("请先配置 DASHSCOPE_API_KEY");
        }
    }
}

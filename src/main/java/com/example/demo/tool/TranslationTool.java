package com.example.demo.tool;

import com.example.demo.service.ai.AlibabaToolService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class TranslationTool implements BotTool {
    private static final Pattern LANGUAGE = Pattern.compile("[A-Za-z][A-Za-z _-]{1,39}");
    private final AlibabaToolService toolService;
    private final ObjectMapper objectMapper;

    public TranslationTool(AlibabaToolService toolService) {
        this.toolService = toolService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String name() {
        return "translate_text";
    }

    @Override
    public String description() {
        return "使用千问翻译模型翻译文本，支持自动识别源语言和中英日韩等多种语言。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "text", Map.of(
                                "type", "string",
                                "description", "需要翻译的原文"
                        ),
                        "source_language", Map.of(
                                "type", "string",
                                "description", "源语言代码或英文名，不确定时填auto"
                        ),
                        "target_language", Map.of(
                                "type", "string",
                                "description", "目标语言代码或英文名，例如zh、en、Japanese"
                        )
                ),
                "required", List.of("text", "target_language"),
                "additionalProperties", false
        );
    }

    @Override
    public String execute(JsonNode arguments) {
        String text = arguments.path("text").asText();
        if (text.isBlank() || text.length() > 8_000) {
            throw new IllegalArgumentException("翻译文本长度必须在1到8000个字符之间");
        }
        String source = arguments.path("source_language").asText("auto").trim();
        String target = arguments.path("target_language").asText().trim();
        requireLanguage(source, "source_language");
        requireLanguage(target, "target_language");
        String translated = toolService.translate(text, source, target);
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "source_language", source,
                    "target_language", target,
                    "translated_text", translated,
                    "model", "qwen-mt-flash"
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成翻译工具结果", exception);
        }
    }

    private void requireLanguage(String language, String field) {
        if (!"auto".equalsIgnoreCase(language) && !LANGUAGE.matcher(language).matches()) {
            throw new IllegalArgumentException("翻译语言参数不合法：" + field);
        }
    }
}

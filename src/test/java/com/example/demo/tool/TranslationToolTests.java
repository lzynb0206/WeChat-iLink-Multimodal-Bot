package com.example.demo.tool;

import com.example.demo.config.AiConfig;
import com.example.demo.service.ai.AlibabaToolService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TranslationToolTests {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void translatesWithAutomaticSourceLanguage() throws Exception {
        StubAlibabaToolService service = new StubAlibabaToolService();
        TranslationTool tool = new TranslationTool(service);

        JsonNode result = objectMapper.readTree(tool.execute(objectMapper.readTree(
                "{\"text\":\"你好\",\"target_language\":\"en\"}")));

        assertEquals("Hello", result.path("translated_text").asText());
        assertEquals("你好", service.text);
        assertEquals("auto", service.source);
        assertEquals("en", service.target);
    }

    @Test
    void rejectsInvalidLanguage() throws Exception {
        TranslationTool tool = new TranslationTool(new StubAlibabaToolService());
        JsonNode arguments = objectMapper.readTree(
                "{\"text\":\"你好\",\"target_language\":\"../en\"}");

        assertThrows(IllegalArgumentException.class, () -> tool.execute(arguments));
    }

    private static class StubAlibabaToolService extends AlibabaToolService {
        private String text;
        private String source;
        private String target;

        StubAlibabaToolService() {
            super(new AiConfig());
        }

        @Override
        public String translate(String text, String sourceLanguage, String targetLanguage) {
            this.text = text;
            this.source = sourceLanguage;
            this.target = targetLanguage;
            return "Hello";
        }
    }
}

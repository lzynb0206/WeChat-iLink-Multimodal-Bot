package com.example.demo.tool;

import com.example.demo.service.ai.AlibabaToolService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Component
public class NewsTool implements BotTool {
    private final AlibabaToolService toolService;
    private final ObjectMapper objectMapper;

    public NewsTool(AlibabaToolService toolService) {
        this.toolService = toolService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String name() {
        return "search_news";
    }

    @Override
    public String description() {
        return "联网查询最新新闻。适合用户询问近期事件、热点、某个主题的最新进展；返回来源和链接。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of(
                                "type", "string",
                                "description", "新闻主题或检索关键词"
                        ),
                        "limit", Map.of(
                                "type", "integer",
                                "minimum", 1,
                                "maximum", 10,
                                "description", "最多返回条数，默认5"
                        )
                ),
                "required", List.of("query"),
                "additionalProperties", false
        );
    }

    @Override
    public String execute(JsonNode arguments) {
        String query = arguments.path("query").asText().trim();
        if (query.isEmpty() || query.length() > 200) {
            throw new IllegalArgumentException("新闻关键词长度必须在1到200个字符之间");
        }
        JsonNode limitNode = arguments.path("limit");
        if (!limitNode.isMissingNode() && !limitNode.isIntegralNumber()) {
            throw new IllegalArgumentException("新闻 limit 必须是整数");
        }
        int limit = limitNode.isMissingNode() ? 5 : limitNode.intValue();
        if (limit < 1 || limit > 10) {
            throw new IllegalArgumentException("新闻 limit 必须在1到10之间");
        }
        String result = toolService.searchNews(query, limit);
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "query", query,
                    "retrieved_at", OffsetDateTime.now().toString(),
                    "result", result,
                    "source", "阿里云百炼联网搜索"
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成新闻工具结果", exception);
        }
    }
}

package com.example.demo.skill;

import com.example.demo.config.DailyBriefSkillConfig;
import com.example.demo.tool.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DailyBriefSkill implements BotSkill {
    private static final Pattern LOCATION = Pattern.compile(
            "(?:城市|地点)\\s*[=：:]\\s*([^,，;；\\s]{1,30})");
    private static final Pattern TOPIC = Pattern.compile(
            "(?:主题|新闻)\\s*[=：:]\\s*([^,，;；]{1,80})");
    private final DailyBriefSkillConfig config;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public DailyBriefSkill(DailyBriefSkillConfig config, ToolRegistry toolRegistry) {
        this.config = config;
        this.toolRegistry = toolRegistry;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String name() {
        return "daily_brief";
    }

    @Override
    public String description() {
        return "生成包含指定城市实况天气和指定主题最新新闻的每日简报。";
    }

    @Override
    public List<String> keywords() {
        return List.of("生成每日简报", "今日简报", "每日简报");
    }

    @Override
    public String execute(String userMessage) {
        String location = extract(LOCATION, userMessage, config.getDefaultLocation());
        String topic = extract(TOPIC, userMessage, config.getDefaultNewsTopic());
        int newsLimit = Math.max(1, Math.min(config.getNewsLimit(), 10));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> weatherFuture = executor.submit(() -> executeToolSafely(
                    "get_current_weather", Map.of("location", location)));
            Future<String> newsFuture = executor.submit(() -> executeToolSafely(
                    "search_news", Map.of("query", topic, "limit", newsLimit)));
            String weather = weatherFuture.get();
            String news = newsFuture.get();
            return """
                    【每日简报】
                    城市：%s
                    天气：%s

                    新闻主题：%s
                    %s
                    """.formatted(location, formatWeather(weather), topic, formatNews(news)).trim();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("每日简报执行被中断", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("每日简报执行失败", exception.getCause());
        }
    }

    private String executeToolSafely(String name, Map<String, Object> arguments) {
        try {
            return toolRegistry.execute(name, objectMapper.writeValueAsString(arguments));
        } catch (Exception exception) {
            try {
                return objectMapper.writeValueAsString(Map.of(
                        "success", false,
                        "error", exception.getMessage() == null
                                ? "工具执行失败" : exception.getMessage()));
            } catch (Exception ignored) {
                return "{\"success\":false,\"error\":\"工具执行失败\"}";
            }
        }
    }

    private String formatWeather(String result) {
        try {
            JsonNode root = objectMapper.readTree(result);
            if (root.has("success") && !root.path("success").asBoolean()) {
                return "获取失败：" + root.path("error").asText("未知错误");
            }
            return "%s，%s，%s℃（更新时间：%s，来源：%s）".formatted(
                    root.path("location").asText("未知地点"),
                    root.path("weather").asText("未知"),
                    root.path("temperature_celsius").asText("未知"),
                    root.path("last_update").asText("未知"),
                    root.path("source").asText("未知"));
        } catch (Exception exception) {
            return result;
        }
    }

    private String formatNews(String result) {
        try {
            JsonNode root = objectMapper.readTree(result);
            if (root.has("success") && !root.path("success").asBoolean()) {
                return "新闻获取失败：" + root.path("error").asText("未知错误");
            }
            String content = root.path("result").asText();
            return StringUtils.hasText(content) ? content : result;
        } catch (Exception exception) {
            return result;
        }
    }

    private String extract(Pattern pattern, String message, String fallback) {
        Matcher matcher = pattern.matcher(message);
        if (matcher.find() && StringUtils.hasText(matcher.group(1))) {
            return matcher.group(1).trim();
        }
        return fallback;
    }
}

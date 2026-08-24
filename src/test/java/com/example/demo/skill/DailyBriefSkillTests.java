package com.example.demo.skill;

import com.example.demo.config.DailyBriefSkillConfig;
import com.example.demo.tool.BotTool;
import com.example.demo.tool.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyBriefSkillTests {
    @Test
    void combinesWeatherAndNewsToolsIntoBrief() {
        AtomicReference<String> weatherLocation = new AtomicReference<>();
        AtomicReference<String> newsQuery = new AtomicReference<>();
        ToolRegistry toolRegistry = new ToolRegistry(List.of(
                new FixedTool("get_current_weather", arguments -> {
                    weatherLocation.set(arguments.path("location").asText());
                    return "{\"location\":\"上海\",\"weather\":\"晴\","
                            + "\"temperature_celsius\":26,\"last_update\":\"2026-08-24T14:00:00+08:00\","
                            + "\"source\":\"心知天气\"}";
                }),
                new FixedTool("search_news", arguments -> {
                    newsQuery.set(arguments.path("query").asText());
                    return "{\"result\":\"1. AI新闻（来源链接）\","
                            + "\"source\":\"阿里云百炼联网搜索\"}";
                })));
        DailyBriefSkillConfig config = new DailyBriefSkillConfig();
        DailyBriefSkill skill = new DailyBriefSkill(config, toolRegistry);

        String reply = skill.execute("生成每日简报 城市=上海，主题=大模型");

        assertEquals("上海", weatherLocation.get());
        assertEquals("大模型", newsQuery.get());
        assertTrue(reply.contains("上海，晴，26℃"));
        assertTrue(reply.contains("AI新闻（来源链接）"));
    }

    private static class FixedTool implements BotTool {
        private final String name;
        private final ToolAction action;

        FixedTool(String name, ToolAction action) {
            this.name = name;
            this.action = action;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return "每日简报测试工具";
        }

        @Override
        public Map<String, Object> parametersSchema() {
            return Map.of("type", "object");
        }

        @Override
        public String execute(JsonNode arguments) {
            return action.execute(arguments);
        }
    }

    @FunctionalInterface
    private interface ToolAction {
        String execute(JsonNode arguments);
    }
}

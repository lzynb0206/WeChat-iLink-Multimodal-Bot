package com.example.demo.skill;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillRegistryTests {
    @Test
    void longestKeywordWinsWhenMultipleSkillsMatch() {
        SkillRegistry registry = new SkillRegistry(List.of(
                new FixedSkill("brief", List.of("简报"), "普通简报"),
                new FixedSkill("daily_brief", List.of("每日简报"), "每日简报结果")));

        SkillExecution result = registry.route("请生成每日简报").orElseThrow();

        assertEquals("daily_brief", result.skillName());
        assertEquals("每日简报结果", result.reply());
    }

    @Test
    void rejectsDuplicateKeyword() {
        assertThrows(IllegalStateException.class, () -> new SkillRegistry(List.of(
                new FixedSkill("first", List.of("今日简报"), "first"),
                new FixedSkill("second", List.of("今日简报"), "second"))));
    }

    private record FixedSkill(
            String name,
            List<String> keywords,
            String reply) implements BotSkill {
        @Override
        public String description() {
            return "测试Skill";
        }

        @Override
        public String execute(String userMessage) {
            return reply;
        }
    }
}

package com.example.demo.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SkillRegistry {
    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final int MAX_MESSAGE_LENGTH = 8_000;
    private final Map<String, SkillKeyword> keywords;

    public SkillRegistry(List<BotSkill> skills) {
        Map<String, SkillKeyword> registered = new LinkedHashMap<>();
        for (BotSkill skill : skills) {
            validate(skill);
            for (String keyword : skill.keywords()) {
                String normalized = normalize(keyword);
                SkillKeyword previous = registered.putIfAbsent(
                        normalized, new SkillKeyword(keyword.trim(), skill));
                if (previous != null) {
                    throw new IllegalStateException("Skill关键词重复：" + keyword);
                }
            }
        }
        this.keywords = Collections.unmodifiableMap(registered);
    }

    public Optional<SkillExecution> route(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return Optional.empty();
        }
        if (userMessage.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("消息长度超过Skill路由限制");
        }

        String normalizedMessage = normalize(userMessage);
        SkillKeyword bestMatch = null;
        int bestLength = -1;
        for (Map.Entry<String, SkillKeyword> entry : keywords.entrySet()) {
            if (normalizedMessage.contains(entry.getKey()) && entry.getKey().length() > bestLength) {
                bestMatch = entry.getValue();
                bestLength = entry.getKey().length();
            }
        }
        if (bestMatch == null) {
            return Optional.empty();
        }

        String reply = bestMatch.skill().execute(userMessage);
        if (!StringUtils.hasText(reply)) {
            throw new IllegalStateException("Skill未返回内容：" + bestMatch.skill().name());
        }
        log.info("Skill执行完成 skill={} keyword={}",
                bestMatch.skill().name(), bestMatch.originalKeyword());
        return Optional.of(new SkillExecution(
                bestMatch.skill().name(), bestMatch.originalKeyword(), reply.trim()));
    }

    private void validate(BotSkill skill) {
        if (skill == null || !StringUtils.hasText(skill.name())
                || !VALID_NAME.matcher(skill.name()).matches()) {
            throw new IllegalStateException("Skill名称必须由字母、数字、下划线或短横线组成，且不超过64个字符");
        }
        if (!StringUtils.hasText(skill.description())) {
            throw new IllegalStateException("Skill缺少说明：" + skill.name());
        }
        if (skill.keywords() == null || skill.keywords().isEmpty()
                || skill.keywords().stream().anyMatch(keyword -> !StringUtils.hasText(keyword))) {
            throw new IllegalStateException("Skill至少需要一个有效关键词：" + skill.name());
        }
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private record SkillKeyword(String originalKeyword, BotSkill skill) {
    }
}

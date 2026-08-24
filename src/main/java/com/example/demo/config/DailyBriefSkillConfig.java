package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "skills.daily-brief")
public class DailyBriefSkillConfig {
    private String defaultLocation = "北京";
    private String defaultNewsTopic = "人工智能";
    private int newsLimit = 3;
}

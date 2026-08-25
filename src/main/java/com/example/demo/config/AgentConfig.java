package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentConfig {
    private int minTasks = 3;
    private int maxTasks = 12;
    private int maxPlanningAttempts = 2;
}

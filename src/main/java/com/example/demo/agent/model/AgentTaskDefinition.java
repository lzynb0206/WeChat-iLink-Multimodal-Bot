package com.example.demo.agent.model;

import java.util.List;

public record AgentTaskDefinition(
        String id,
        String title,
        String description,
        String capability,
        List<String> dependsOn,
        String expectedOutput,
        int maxAttempts) {
    public AgentTaskDefinition {
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }
}

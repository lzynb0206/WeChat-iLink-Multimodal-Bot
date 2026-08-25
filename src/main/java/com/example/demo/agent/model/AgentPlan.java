package com.example.demo.agent.model;

import java.util.List;

public record AgentPlan(
        String goal,
        String summary,
        List<AgentTaskDefinition> tasks,
        List<String> completionCriteria) {
    public AgentPlan {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        completionCriteria = completionCriteria == null
                ? List.of() : List.copyOf(completionCriteria);
    }
}

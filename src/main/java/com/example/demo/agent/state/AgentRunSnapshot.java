package com.example.demo.agent.state;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AgentRunSnapshot(
        UUID runId,
        String goal,
        AgentRunStatus status,
        Instant createdAt,
        Instant updatedAt,
        List<AgentTaskState> tasks) {
    public AgentRunSnapshot {
        tasks = List.copyOf(tasks);
    }
}

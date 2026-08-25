package com.example.demo.agent.state;

import java.time.Instant;

public record AgentTaskState(
        String taskId,
        AgentTaskStatus status,
        int attempts,
        String result,
        String error,
        Instant startedAt,
        Instant completedAt) {
}

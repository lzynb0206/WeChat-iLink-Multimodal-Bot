package com.example.demo.agent.state;

import com.example.demo.agent.model.AgentPlan;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class AgentRun {
    private final UUID id;
    private final AgentPlan plan;
    private final Instant createdAt;
    private final Map<String, AgentTaskState> taskStates;
    private AgentRunStatus status;
    private Instant updatedAt;

    AgentRun(AgentPlan plan) {
        this.id = UUID.randomUUID();
        this.plan = plan;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        this.status = AgentRunStatus.PLANNED;
        this.taskStates = new LinkedHashMap<>();
        plan.tasks().forEach(task -> taskStates.put(
                task.id(),
                new AgentTaskState(
                        task.id(), AgentTaskStatus.PENDING, 0,
                        null, null, null, null)));
    }

    public UUID id() {
        return id;
    }

    public AgentPlan plan() {
        return plan;
    }

    Map<String, AgentTaskState> taskStates() {
        return taskStates;
    }

    AgentRunStatus status() {
        return status;
    }

    void status(AgentRunStatus status) {
        this.status = status;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    void touch() {
        this.updatedAt = Instant.now();
    }
}

package com.example.demo.agent.model;

public record AgentCapability(
        String name,
        AgentCapabilityType type,
        String description) {
}

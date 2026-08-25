package com.example.demo.agent.planner;

@FunctionalInterface
public interface AgentPlanningClient {
    String generatePlanJson(String prompt);
}

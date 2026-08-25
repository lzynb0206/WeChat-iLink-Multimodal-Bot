package com.example.demo.agent.planner;

public class AgentPlanValidationException extends IllegalArgumentException {
    public AgentPlanValidationException(String message) {
        super(message);
    }

    public AgentPlanValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

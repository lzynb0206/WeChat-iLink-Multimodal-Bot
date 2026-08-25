package com.example.demo.agent.planner;

import com.example.demo.service.ai.AlibabaAiService;
import org.springframework.stereotype.Component;

@Component
public class AlibabaAgentPlanningClient implements AgentPlanningClient {
    private final AlibabaAiService aiService;

    public AlibabaAgentPlanningClient(AlibabaAiService aiService) {
        this.aiService = aiService;
    }

    @Override
    public String generatePlanJson(String prompt) {
        return aiService.createAgentPlan(prompt);
    }
}

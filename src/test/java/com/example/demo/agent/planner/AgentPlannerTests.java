package com.example.demo.agent.planner;

import com.example.demo.agent.AgentTestFixtures;
import com.example.demo.agent.model.AgentPlan;
import com.example.demo.config.AgentConfig;
import com.example.demo.config.RagConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPlannerTests {
    @Test
    void repairsInvalidFirstPlanAndKeepsOriginalGoal() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentPlan validPlan = AgentTestFixtures.validPlan();
        AgentPlan tooShort = new AgentPlan(
                "模型修改过的目标",
                "任务数量不足",
                validPlan.tasks().subList(0, 2),
                validPlan.completionCriteria());
        List<String> prompts = new ArrayList<>();
        AgentPlanningClient client = prompt -> {
            prompts.add(prompt);
            try {
                return objectMapper.writeValueAsString(
                        prompts.size() == 1 ? tooShort : validPlan);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        };
        AgentConfig config = new AgentConfig();
        config.setMaxPlanningAttempts(2);
        AgentPlanner planner = new AgentPlanner(
                client,
                new AgentCapabilityCatalog(List.of(), List.of(), new RagConfig()),
                new AgentPlanValidator(config),
                config);

        String originalGoal = "把项目整理成可以开源和面试展示的完整交付包";
        AgentPlan result = planner.createPlan(originalGoal, AgentTestFixtures.capabilities());

        assertEquals(originalGoal, result.goal());
        assertEquals(3, result.tasks().size());
        assertEquals(2, prompts.size());
        assertTrue(prompts.get(1).contains("上一次计划未通过校验"));
    }
}

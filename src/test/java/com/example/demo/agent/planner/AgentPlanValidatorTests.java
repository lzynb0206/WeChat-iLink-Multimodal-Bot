package com.example.demo.agent.planner;

import com.example.demo.agent.AgentTestFixtures;
import com.example.demo.agent.model.AgentPlan;
import com.example.demo.config.AgentConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentPlanValidatorTests {
    private final AgentPlanValidator validator = new AgentPlanValidator(new AgentConfig());

    @Test
    void acceptsValidClosedLoopDag() {
        assertDoesNotThrow(() -> validator.validate(
                AgentTestFixtures.validPlan(), AgentTestFixtures.capabilities()));
    }

    @Test
    void rejectsCircularDependencies() {
        AgentPlan invalid = new AgentPlan(
                "循环计划",
                "这是一个错误计划",
                List.of(
                        AgentTestFixtures.task("first", "inspect_project", List.of("final_task"), 1),
                        AgentTestFixtures.task("second", "security_scan", List.of("first"), 1),
                        AgentTestFixtures.task("final_task", "llm_reasoning", List.of("second"), 1)),
                List.of("完成"));

        assertThrows(AgentPlanValidationException.class, () -> validator.validate(
                invalid, AgentTestFixtures.capabilities()));
    }

    @Test
    void rejectsPlanWithoutFinalSynthesisTask() {
        AgentPlan invalid = new AgentPlan(
                "不闭环计划",
                "三个任务没有汇总",
                List.of(
                        AgentTestFixtures.task("first", "inspect_project", List.of(), 1),
                        AgentTestFixtures.task("second", "security_scan", List.of(), 1),
                        AgentTestFixtures.task("third", "llm_reasoning", List.of("first"), 1)),
                List.of("完成"));

        assertThrows(AgentPlanValidationException.class, () -> validator.validate(
                invalid, AgentTestFixtures.capabilities()));
    }

    @Test
    void rejectsUnknownCapability() {
        AgentPlan valid = AgentTestFixtures.validPlan();
        var tasks = List.of(
                AgentTestFixtures.task("inspect", "invented_tool", List.of(), 1),
                valid.tasks().get(1),
                valid.tasks().get(2));
        AgentPlan invalid = new AgentPlan(
                valid.goal(), valid.summary(), tasks, valid.completionCriteria());

        assertThrows(AgentPlanValidationException.class, () -> validator.validate(
                invalid, AgentTestFixtures.capabilities()));
    }
}

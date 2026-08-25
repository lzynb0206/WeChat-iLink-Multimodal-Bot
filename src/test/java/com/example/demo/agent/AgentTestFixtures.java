package com.example.demo.agent;

import com.example.demo.agent.model.AgentCapability;
import com.example.demo.agent.model.AgentCapabilityType;
import com.example.demo.agent.model.AgentPlan;
import com.example.demo.agent.model.AgentTaskDefinition;

import java.util.List;

public final class AgentTestFixtures {
    private AgentTestFixtures() {
    }

    public static List<AgentCapability> capabilities() {
        return List.of(
                new AgentCapability(
                        "inspect_project", AgentCapabilityType.TOOL, "分析项目结构"),
                new AgentCapability(
                        "security_scan", AgentCapabilityType.TOOL, "检查敏感信息"),
                new AgentCapability(
                        "llm_reasoning", AgentCapabilityType.LLM, "生成最终交付物"));
    }

    public static AgentPlan validPlan() {
        return new AgentPlan(
                "生成项目开源交付报告",
                "先并行检查项目和安全问题，再汇总完整报告。",
                List.of(
                        task("inspect", "inspect_project", List.of(), 2),
                        task("security", "security_scan", List.of(), 2),
                        task("final_report", "llm_reasoning", List.of("inspect", "security"), 1)),
                List.of("报告包含项目分析和安全检查结果"));
    }

    public static AgentTaskDefinition task(
            String id,
            String capability,
            List<String> dependencies,
            int maxAttempts) {
        return new AgentTaskDefinition(
                id,
                "任务" + id,
                "执行" + id + "并返回结构化结果",
                capability,
                dependencies,
                id + "的可验证结果",
                maxAttempts);
    }
}

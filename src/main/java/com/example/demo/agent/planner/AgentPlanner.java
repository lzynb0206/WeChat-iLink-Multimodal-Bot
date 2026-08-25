package com.example.demo.agent.planner;

import com.example.demo.agent.model.AgentCapability;
import com.example.demo.agent.model.AgentPlan;
import com.example.demo.config.AgentConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class AgentPlanner {
    private static final int MAX_GOAL_LENGTH = 2_000;
    private final AgentPlanningClient planningClient;
    private final AgentCapabilityCatalog capabilityCatalog;
    private final AgentPlanValidator validator;
    private final AgentConfig config;
    private final ObjectMapper objectMapper;

    public AgentPlanner(
            AgentPlanningClient planningClient,
            AgentCapabilityCatalog capabilityCatalog,
            AgentPlanValidator validator,
            AgentConfig config) {
        this.planningClient = planningClient;
        this.capabilityCatalog = capabilityCatalog;
        this.validator = validator;
        this.config = config;
        this.objectMapper = new ObjectMapper();
    }

    public AgentPlan createPlan(String goal) {
        return createPlan(goal, capabilityCatalog.availableCapabilities());
    }

    public AgentPlan createPlan(String goal, List<AgentCapability> capabilities) {
        if (!StringUtils.hasText(goal) || goal.length() > MAX_GOAL_LENGTH) {
            throw new IllegalArgumentException("Agent最终目标不能为空且不能超过2000个字符");
        }
        int attempts = Math.max(1, Math.min(config.getMaxPlanningAttempts(), 3));
        String repairFeedback = "";
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            String prompt = buildPrompt(goal.trim(), capabilities, repairFeedback);
            try {
                String response = planningClient.generatePlanJson(prompt);
                AgentPlan draft = objectMapper.readValue(stripCodeFence(response), AgentPlan.class);
                AgentPlan plan = new AgentPlan(
                        goal.trim(), draft.summary(), draft.tasks(), draft.completionCriteria());
                validator.validate(plan, capabilities);
                log.info("Agent规划完成 tasks={} attempts={}", plan.tasks().size(), attempt);
                return plan;
            } catch (Exception exception) {
                lastFailure = exception;
                repairFeedback = "上一次计划未通过校验，请修正后重新输出。错误："
                        + safeMessage(exception);
                log.warn("Agent计划第{}次生成失败：{}", attempt, safeMessage(exception));
            }
        }
        throw new AgentPlanValidationException(
                "Agent在%d次尝试后仍未生成有效计划：%s"
                        .formatted(attempts, safeMessage(lastFailure)),
                lastFailure);
    }

    private String buildPrompt(
            String goal,
            List<AgentCapability> capabilities,
            String repairFeedback) {
        try {
            String capabilityJson = objectMapper.writeValueAsString(capabilities);
            return """
                    你是自主规划型Agent的Planner。用户只提供最终目标，你必须自主拆解任务，不要向用户索要步骤。

                    最终目标：
                    %s

                    当前确实可用的能力如下，只能使用这些能力名称，不能编造工具：
                    %s

                    规划规则：
                    1. 生成%d到%d个具体任务，至少使用两种不同能力。
                    2. id只能使用小写字母、数字、下划线和短横线，并以小写字母开头。
                    3. dependsOn填写前置任务id；无依赖时为空数组；不得出现循环或不存在的依赖。
                    4. 可并行的任务不要互相依赖；需要前一步结果的任务必须声明依赖。
                    5. maxAttempts只能是1到3；外部网络能力建议2，纯本地整理任务建议1。
                    6. 必须设计一个最终汇总任务，并通过直接或间接依赖覆盖其余所有任务，最终输出完整成品。
                    7. expectedOutput必须明确、可验证，completionCriteria用于判断整个目标是否完成。
                    8. 只输出JSON对象，不要Markdown代码块，不要解释。

                    JSON结构：
                    {
                      "goal": "用户最终目标",
                      "summary": "整体执行思路",
                      "tasks": [
                        {
                          "id": "task_id",
                          "title": "任务标题",
                          "description": "具体要做什么",
                          "capability": "能力名称",
                          "dependsOn": [],
                          "expectedOutput": "本任务应产生什么结果",
                          "maxAttempts": 2
                        }
                      ],
                      "completionCriteria": ["可验证的完成标准"]
                    }

                    %s
                    """.formatted(
                    goal,
                    capabilityJson,
                    config.getMinTasks(),
                    config.getMaxTasks(),
                    repairFeedback).trim();
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成Agent规划提示词", exception);
        }
    }

    private String stripCodeFence(String response) {
        if (!StringUtils.hasText(response)) {
            throw new AgentPlanValidationException("规划模型未返回内容");
        }
        String trimmed = response.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        int closingFence = trimmed.lastIndexOf("```");
        if (firstLineEnd < 0 || closingFence <= firstLineEnd) {
            throw new AgentPlanValidationException("规划模型返回了不完整的Markdown代码块");
        }
        return trimmed.substring(firstLineEnd + 1, closingFence).trim();
    }

    private String safeMessage(Exception exception) {
        if (exception == null || !StringUtils.hasText(exception.getMessage())) {
            return "未知错误";
        }
        String message = exception.getMessage().replaceAll("[\\r\\n]+", " ");
        return message.length() > 300 ? message.substring(0, 300) : message;
    }
}

package com.example.demo.agent.planner;

import com.example.demo.agent.model.AgentCapability;
import com.example.demo.agent.model.AgentCapabilityType;
import com.example.demo.config.RagConfig;
import com.example.demo.skill.BotSkill;
import com.example.demo.tool.BotTool;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AgentCapabilityCatalog {
    private final List<BotTool> tools;
    private final List<BotSkill> skills;
    private final RagConfig ragConfig;

    public AgentCapabilityCatalog(
            List<BotTool> tools,
            List<BotSkill> skills,
            RagConfig ragConfig) {
        this.tools = List.copyOf(tools);
        this.skills = List.copyOf(skills);
        this.ragConfig = ragConfig;
    }

    public List<AgentCapability> availableCapabilities() {
        Map<String, AgentCapability> capabilities = new LinkedHashMap<>();
        tools.forEach(tool -> add(capabilities, new AgentCapability(
                tool.name(), AgentCapabilityType.TOOL, tool.description())));
        skills.forEach(skill -> add(capabilities, new AgentCapability(
                skill.name(), AgentCapabilityType.SKILL, skill.description())));
        if (ragConfig.isEnabled()) {
            add(capabilities, new AgentCapability(
                    "rag_retrieval",
                    AgentCapabilityType.RAG,
                    "从本地项目知识库检索相关事实，并为后续任务提供可信上下文。"));
        }
        add(capabilities, new AgentCapability(
                "llm_reasoning",
                AgentCapabilityType.LLM,
                "综合已有任务结果、进行推理并生成结构化总结或最终交付物。"));
        return capabilities.values().stream()
                .sorted(Comparator.comparing(AgentCapability::name))
                .toList();
    }

    private void add(
            Map<String, AgentCapability> capabilities,
            AgentCapability capability) {
        AgentCapability previous = capabilities.putIfAbsent(capability.name(), capability);
        if (previous != null) {
            throw new IllegalStateException(
                    "Agent能力名称跨模块重复：" + capability.name());
        }
    }
}

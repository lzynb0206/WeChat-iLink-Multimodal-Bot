package com.example.demo.agent.planner;

import com.example.demo.agent.model.AgentCapabilityType;
import com.example.demo.config.RagConfig;
import com.example.demo.skill.BotSkill;
import com.example.demo.tool.BotTool;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCapabilityCatalogTests {
    @Test
    void collectsToolSkillRagAndLlmCapabilities() {
        BotTool tool = new BotTool() {
            public String name() { return "inspect_project"; }
            public String description() { return "分析项目"; }
            public Map<String, Object> parametersSchema() { return Map.of("type", "object"); }
            public String execute(JsonNode arguments) { return "{}"; }
        };
        BotSkill skill = new BotSkill() {
            public String name() { return "release_check"; }
            public String description() { return "发布检查"; }
            public List<String> keywords() { return List.of("发布检查"); }
            public String execute(String userMessage) { return "完成"; }
        };
        AgentCapabilityCatalog catalog = new AgentCapabilityCatalog(
                List.of(tool), List.of(skill), new RagConfig());

        var capabilities = catalog.availableCapabilities();

        assertEquals(4, capabilities.size());
        assertTrue(capabilities.stream().anyMatch(capability ->
                capability.name().equals("inspect_project")
                        && capability.type() == AgentCapabilityType.TOOL));
        assertTrue(capabilities.stream().anyMatch(capability ->
                capability.name().equals("release_check")
                        && capability.type() == AgentCapabilityType.SKILL));
        assertTrue(capabilities.stream().anyMatch(capability ->
                capability.type() == AgentCapabilityType.RAG));
        assertTrue(capabilities.stream().anyMatch(capability ->
                capability.type() == AgentCapabilityType.LLM));
    }
}

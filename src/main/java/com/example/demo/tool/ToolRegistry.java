package com.example.demo.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolRegistry {
    private final Map<String, BotTool> tools;
    private final ObjectMapper objectMapper;

    public ToolRegistry(List<BotTool> toolList) {
        this.tools = new LinkedHashMap<>();
        this.objectMapper = new ObjectMapper();
        for (BotTool tool : toolList) {
            if (tools.putIfAbsent(tool.name(), tool) != null) {
                throw new IllegalStateException("工具名称重复：" + tool.name());
            }
        }
    }

    public List<Map<String, Object>> definitions() {
        return tools.values().stream().map(BotTool::definition).toList();
    }

    public String execute(String name, String argumentsJson) {
        BotTool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("不存在的工具：" + name);
        }
        try {
            JsonNode arguments = objectMapper.readTree(argumentsJson);
            if (!arguments.isObject()) {
                throw new IllegalArgumentException("工具参数必须是 JSON 对象");
            }
            return tool.execute(arguments);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("工具参数不是有效 JSON", exception);
        }
    }
}

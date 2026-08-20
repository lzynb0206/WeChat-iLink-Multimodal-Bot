package com.example.demo.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class ToolRegistry {
    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final int MAX_ARGUMENT_LENGTH = 20_000;
    private final Map<String, BotTool> tools;
    private final ObjectMapper objectMapper;

    public ToolRegistry(List<BotTool> toolList) {
        this.objectMapper = new ObjectMapper();
        Map<String, BotTool> registeredTools = new LinkedHashMap<>();
        for (BotTool tool : toolList) {
            validateDefinition(tool);
            if (registeredTools.putIfAbsent(tool.name(), tool) != null) {
                throw new IllegalStateException("工具名称重复：" + tool.name());
            }
        }
        this.tools = Collections.unmodifiableMap(registeredTools);
    }

    public List<Map<String, Object>> definitions() {
        return tools.values().stream().map(BotTool::definition).toList();
    }

    public String execute(String name, String argumentsJson) {
        BotTool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("不存在的工具：" + name);
        }
        if (argumentsJson == null || argumentsJson.length() > MAX_ARGUMENT_LENGTH) {
            throw new IllegalArgumentException("工具参数为空或长度超过限制");
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

    private void validateDefinition(BotTool tool) {
        if (tool == null || tool.name() == null || !VALID_NAME.matcher(tool.name()).matches()) {
            throw new IllegalStateException("工具名称必须由字母、数字、下划线或短横线组成，且不超过64个字符");
        }
        if (tool.description() == null || tool.description().isBlank()) {
            throw new IllegalStateException("工具缺少说明：" + tool.name());
        }
        Map<String, Object> schema = tool.parametersSchema();
        Object schemaType = schema == null ? null : schema.get("type");
        if (!"object".equals(schemaType)) {
            throw new IllegalStateException("工具参数 Schema 的 type 必须是 object：" + tool.name());
        }
    }
}

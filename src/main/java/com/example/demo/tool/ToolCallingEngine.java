package com.example.demo.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Component
public class ToolCallingEngine {
    private static final int MAX_TOOL_ROUNDS = 6;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public ToolCallingEngine(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        this.objectMapper = new ObjectMapper();
    }

    public List<Map<String, Object>> toolDefinitions() {
        return toolRegistry.definitions();
    }

    public String run(
            List<Map<String, Object>> initialMessages,
            Function<List<Map<String, Object>>, JsonNode> modelCall) {
        List<Map<String, Object>> messages = new ArrayList<>(initialMessages);
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            JsonNode message = modelCall.apply(List.copyOf(messages));
            if (message == null || message.isMissingNode() || !message.isObject()) {
                throw new IllegalStateException("模型未返回有效消息");
            }

            JsonNode toolCalls = message.path("tool_calls");
            if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                String content = message.path("content").asText();
                if (!StringUtils.hasText(content)) {
                    throw new IllegalStateException("模型未返回最终回答");
                }
                return content.trim();
            }

            messages.add(toMap(message));
            for (JsonNode toolCall : toolCalls) {
                messages.add(executeToolCall(toolCall));
            }
        }
        throw new IllegalStateException("工具调用轮数超过限制：" + MAX_TOOL_ROUNDS);
    }

    private Map<String, Object> executeToolCall(JsonNode toolCall) {
        String toolCallId = toolCall.path("id").asText();
        String toolName = toolCall.at("/function/name").asText();
        JsonNode argumentsNode = toolCall.at("/function/arguments");
        if (!StringUtils.hasText(toolCallId) || !StringUtils.hasText(toolName)
                || argumentsNode.isMissingNode()) {
            throw new IllegalStateException("模型返回的工具调用缺少 id、名称或参数");
        }
        String arguments = argumentsNode.isTextual()
                ? argumentsNode.asText()
                : argumentsNode.toString();
        String result;
        try {
            result = toolRegistry.execute(toolName, arguments);
            log.info("工具执行成功 tool={}", toolName);
        } catch (Exception exception) {
            result = errorResult(exception);
            log.warn("工具执行失败 tool={}", toolName, exception);
        }
        return Map.of(
                "role", "tool",
                "tool_call_id", toolCallId,
                "content", result
        );
    }

    private Map<String, Object> toMap(JsonNode node) {
        try {
            return objectMapper.readValue(
                    node.toString(), new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception exception) {
            throw new IllegalStateException("无法保存工具调用消息", exception);
        }
    }

    private String errorResult(Exception exception) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "success", false,
                    "error", exception.getMessage() == null ? "工具执行失败" : exception.getMessage()
            ));
        } catch (Exception ignored) {
            return "{\"success\":false,\"error\":\"工具执行失败\"}";
        }
    }
}

package com.example.demo.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolCallingEngineTests {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void laterToolUsesValueReturnedByPreviousTool() throws Exception {
        ToolRegistry registry = new ToolRegistry(List.of(
                new FixedWeatherTool(), new TemperatureConverterTool()));
        ToolCallingEngine engine = new ToolCallingEngine(registry);
        AtomicInteger round = new AtomicInteger();

        JsonNode weatherCall = toolCallMessage(
                "call_weather",
                "get_current_weather",
                Map.of("location", "张家港"));
        JsonNode finalAnswer = objectMapper.readTree(
                "{\"role\":\"assistant\",\"content\":\"张家港当前20℃，换算后是68℉。\"}");

        String answer = engine.run(
                List.of(Map.of("role", "user", "content", "查询张家港天气并换算成华氏度")),
                messages -> switch (round.getAndIncrement()) {
                    case 0 -> weatherCall;
                    case 1 -> temperatureCallUsingPreviousResult(messages);
                    case 2 -> finalAnswerAfterCheckingConversion(messages, finalAnswer);
                    default -> throw new AssertionError("模型调用次数超过预期");
                });

        assertEquals("张家港当前20℃，换算后是68℉。", answer);
        assertEquals(3, round.get());
    }

    private JsonNode temperatureCallUsingPreviousResult(List<Map<String, Object>> messages) {
        try {
            Map<String, Object> weatherMessage = messages.get(messages.size() - 1);
            assertEquals("tool", weatherMessage.get("role"));
            JsonNode weatherResult = objectMapper.readTree((String) weatherMessage.get("content"));
            int temperature = weatherResult.path("temperature_celsius").intValue();
            assertEquals(20, temperature);
            return toolCallMessage(
                    "call_convert",
                    "convert_temperature",
                    Map.of("value", temperature, "from_unit", "C", "to_unit", "F"));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private JsonNode finalAnswerAfterCheckingConversion(
            List<Map<String, Object>> messages, JsonNode finalAnswer) {
        try {
            Map<String, Object> conversionMessage = messages.get(messages.size() - 1);
            JsonNode conversionResult = objectMapper.readTree(
                    (String) conversionMessage.get("content"));
            assertEquals(68, conversionResult.path("value").intValue());
            assertEquals("F", conversionResult.path("unit").asText());
            return finalAnswer;
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private JsonNode toolCallMessage(
            String callId, String toolName, Map<String, Object> arguments) {
        var message = objectMapper.createObjectNode();
        message.put("role", "assistant");
        message.putNull("content");
        var toolCall = message.putArray("tool_calls").addObject();
        toolCall.put("id", callId);
        toolCall.put("type", "function");
        var function = toolCall.putObject("function");
        function.put("name", toolName);
        try {
            function.put("arguments", objectMapper.writeValueAsString(arguments));
            return message;
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static class FixedWeatherTool implements BotTool {
        @Override
        public String name() {
            return "get_current_weather";
        }

        @Override
        public String description() {
            return "返回测试城市的固定天气";
        }

        @Override
        public Map<String, Object> parametersSchema() {
            return Map.of("type", "object");
        }

        @Override
        public String execute(JsonNode arguments) {
            return "{\"location\":\"张家港\",\"weather\":\"晴\",\"temperature_celsius\":20}";
        }
    }
}

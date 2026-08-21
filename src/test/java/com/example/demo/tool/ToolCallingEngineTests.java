package com.example.demo.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void independentToolsReturnedInSameRoundRunInParallel() throws Exception {
        CountDownLatch bothStarted = new CountDownLatch(2);
        AtomicInteger activeCalls = new AtomicInteger();
        AtomicInteger maxActiveCalls = new AtomicInteger();
        ToolRegistry registry = new ToolRegistry(List.of(
                new ParallelProbeTool("first_tool", bothStarted, activeCalls, maxActiveCalls),
                new ParallelProbeTool("second_tool", bothStarted, activeCalls, maxActiveCalls)));
        ToolCallingEngine engine = new ToolCallingEngine(registry);
        AtomicInteger round = new AtomicInteger();

        JsonNode parallelCalls = twoToolCallMessage();
        JsonNode finalAnswer = objectMapper.readTree(
                "{\"role\":\"assistant\",\"content\":\"两个独立工具均已完成。\"}");

        String answer = engine.run(
                List.of(Map.of("role", "user", "content", "同时执行两个独立任务")),
                messages -> switch (round.getAndIncrement()) {
                    case 0 -> parallelCalls;
                    case 1 -> finalAnswerAfterCheckingParallelResults(messages, finalAnswer);
                    default -> throw new AssertionError("模型调用次数超过预期");
                });

        assertEquals("两个独立工具均已完成。", answer);
        assertEquals(2, maxActiveCalls.get());
        assertEquals(2, round.get());
    }

    @Test
    void rejectsUnboundedParallelToolCalls() {
        ToolCallingEngine engine = new ToolCallingEngine(
                new ToolRegistry(List.of(new FixedWeatherTool())));
        var message = objectMapper.createObjectNode();
        message.put("role", "assistant");
        message.putNull("content");
        var toolCalls = message.putArray("tool_calls");
        for (int index = 0; index < 9; index++) {
            addToolCall(toolCalls, "call_" + index, "get_current_weather");
        }

        assertThrows(IllegalStateException.class, () -> engine.run(
                List.of(Map.of("role", "user", "content", "执行很多工具")),
                messages -> message));
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

    private JsonNode finalAnswerAfterCheckingParallelResults(
            List<Map<String, Object>> messages, JsonNode finalAnswer) {
        assertEquals(4, messages.size());
        assertEquals("call_first", messages.get(2).get("tool_call_id"));
        assertEquals("call_second", messages.get(3).get("tool_call_id"));
        assertEquals("{\"tool\":\"first_tool\"}", messages.get(2).get("content"));
        assertEquals("{\"tool\":\"second_tool\"}", messages.get(3).get("content"));
        return finalAnswer;
    }

    private JsonNode twoToolCallMessage() {
        var message = objectMapper.createObjectNode();
        message.put("role", "assistant");
        message.putNull("content");
        var toolCalls = message.putArray("tool_calls");
        addToolCall(toolCalls, "call_first", "first_tool");
        addToolCall(toolCalls, "call_second", "second_tool");
        return message;
    }

    private void addToolCall(
            com.fasterxml.jackson.databind.node.ArrayNode toolCalls,
            String callId,
            String toolName) {
        var toolCall = toolCalls.addObject();
        toolCall.put("id", callId);
        toolCall.put("type", "function");
        var function = toolCall.putObject("function");
        function.put("name", toolName);
        function.put("arguments", "{}");
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

    private static class ParallelProbeTool implements BotTool {
        private final String toolName;
        private final CountDownLatch bothStarted;
        private final AtomicInteger activeCalls;
        private final AtomicInteger maxActiveCalls;

        ParallelProbeTool(
                String toolName,
                CountDownLatch bothStarted,
                AtomicInteger activeCalls,
                AtomicInteger maxActiveCalls) {
            this.toolName = toolName;
            this.bothStarted = bothStarted;
            this.activeCalls = activeCalls;
            this.maxActiveCalls = maxActiveCalls;
        }

        @Override
        public String name() {
            return toolName;
        }

        @Override
        public String description() {
            return "并行执行测试工具";
        }

        @Override
        public Map<String, Object> parametersSchema() {
            return Map.of("type", "object");
        }

        @Override
        public String execute(JsonNode arguments) {
            int currentCalls = activeCalls.incrementAndGet();
            maxActiveCalls.accumulateAndGet(currentCalls, Math::max);
            bothStarted.countDown();
            try {
                assertTrue(bothStarted.await(2, TimeUnit.SECONDS), "两个工具没有并行启动");
                return "{\"tool\":\"" + toolName + "\"}";
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("并行测试被中断", exception);
            } finally {
                activeCalls.decrementAndGet();
            }
        }
    }
}

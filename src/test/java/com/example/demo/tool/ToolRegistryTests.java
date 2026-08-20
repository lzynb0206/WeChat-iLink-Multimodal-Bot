package com.example.demo.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolRegistryTests {

    @Test
    void rejectsDuplicateToolNames() {
        BotTool first = new EchoTool("echo");
        BotTool second = new EchoTool("echo");

        assertThrows(IllegalStateException.class,
                () -> new ToolRegistry(List.of(first, second)));
    }

    @Test
    void rejectsMalformedArgumentsBeforeExecutingTool() {
        ToolRegistry registry = new ToolRegistry(List.of(new EchoTool("echo")));

        assertThrows(IllegalArgumentException.class,
                () -> registry.execute("echo", "not-json"));
    }

    private record EchoTool(String name) implements BotTool {
        @Override
        public String description() {
            return "返回输入参数";
        }

        @Override
        public Map<String, Object> parametersSchema() {
            return Map.of("type", "object");
        }

        @Override
        public String execute(JsonNode arguments) {
            return arguments.toString();
        }
    }
}

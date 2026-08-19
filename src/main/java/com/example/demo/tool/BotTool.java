package com.example.demo.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public interface BotTool {
    String name();

    String description();

    Map<String, Object> parametersSchema();

    String execute(JsonNode arguments);

    default Map<String, Object> definition() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name(),
                        "description", description(),
                        "parameters", parametersSchema()
                )
        );
    }
}

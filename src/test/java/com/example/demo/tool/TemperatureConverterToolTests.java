package com.example.demo.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemperatureConverterToolTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TemperatureConverterTool tool = new TemperatureConverterTool();

    @Test
    void convertsCelsiusToFahrenheit() throws Exception {
        JsonNode result = objectMapper.readTree(tool.execute(objectMapper.readTree(
                "{\"value\":20,\"from_unit\":\"C\",\"to_unit\":\"F\"}")));
        assertEquals(68, result.path("value").intValue());
        assertEquals("F", result.path("unit").asText());
    }

    @Test
    void rejectsTemperatureBelowAbsoluteZero() {
        assertThrows(IllegalArgumentException.class, () -> tool.execute(objectMapper.readTree(
                "{\"value\":-274,\"from_unit\":\"C\",\"to_unit\":\"K\"}")));
    }
}

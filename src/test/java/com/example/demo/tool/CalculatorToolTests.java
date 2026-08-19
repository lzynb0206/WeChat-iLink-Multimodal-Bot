package com.example.demo.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalculatorToolTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CalculatorTool calculatorTool = new CalculatorTool();

    @Test
    void calculatesDecimalMultiplicationExactly() throws Exception {
        String result = calculatorTool.execute(
                objectMapper.readTree("{\"operation\":\"multiply\",\"left\":0.1,\"right\":0.2}"));
        assertEquals("0.02", result);
    }

    @Test
    void rejectsDivisionByZero() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> calculatorTool.execute(objectMapper.readTree(
                        "{\"operation\":\"divide\",\"left\":10,\"right\":0}")));
    }

    @Test
    void schemaRejectsAdditionalParameters() {
        assertEquals("object", calculatorTool.parametersSchema().get("type"));
        assertFalse((Boolean) calculatorTool.parametersSchema().get("additionalProperties"));
    }
}

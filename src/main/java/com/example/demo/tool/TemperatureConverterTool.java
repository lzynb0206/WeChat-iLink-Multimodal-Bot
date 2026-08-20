package com.example.demo.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Map;

@Component
public class TemperatureConverterTool implements BotTool {
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL64;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "convert_temperature";
    }

    @Override
    public String description() {
        return "在摄氏度C、华氏度F和开尔文K之间换算温度。输入温度来自天气工具时，应使用天气工具返回的数值。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "value", Map.of(
                                "type", "number",
                                "description", "需要换算的温度数值"
                        ),
                        "from_unit", Map.of(
                                "type", "string",
                                "enum", List.of("C", "F", "K"),
                                "description", "原始温度单位"
                        ),
                        "to_unit", Map.of(
                                "type", "string",
                                "enum", List.of("C", "F", "K"),
                                "description", "目标温度单位"
                        )
                ),
                "required", List.of("value", "from_unit", "to_unit"),
                "additionalProperties", false
        );
    }

    @Override
    public String execute(JsonNode arguments) {
        if (!arguments.path("value").isNumber()) {
            throw new IllegalArgumentException("温度换算工具缺少数字参数 value");
        }
        String fromUnit = requireUnit(arguments, "from_unit");
        String toUnit = requireUnit(arguments, "to_unit");
        BigDecimal value = arguments.path("value").decimalValue();
        BigDecimal celsius = toCelsius(value, fromUnit);
        if (celsius.compareTo(new BigDecimal("-273.15")) < 0) {
            throw new IllegalArgumentException("温度不能低于绝对零度");
        }
        BigDecimal converted = fromCelsius(celsius, toUnit)
                .stripTrailingZeros();
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "value", converted,
                    "unit", toUnit
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成温度换算结果", exception);
        }
    }

    private String requireUnit(JsonNode arguments, String field) {
        String unit = arguments.path(field).asText();
        if (!List.of("C", "F", "K").contains(unit)) {
            throw new IllegalArgumentException("不支持的温度单位：" + unit);
        }
        return unit;
    }

    private BigDecimal toCelsius(BigDecimal value, String unit) {
        return switch (unit) {
            case "C" -> value;
            case "F" -> value.subtract(new BigDecimal("32"))
                    .multiply(new BigDecimal("5"), MATH_CONTEXT)
                    .divide(new BigDecimal("9"), MATH_CONTEXT);
            case "K" -> value.subtract(new BigDecimal("273.15"), MATH_CONTEXT);
            default -> throw new IllegalArgumentException("不支持的温度单位：" + unit);
        };
    }

    private BigDecimal fromCelsius(BigDecimal celsius, String unit) {
        return switch (unit) {
            case "C" -> celsius;
            case "F" -> celsius.multiply(new BigDecimal("9"), MATH_CONTEXT)
                    .divide(new BigDecimal("5"), MATH_CONTEXT)
                    .add(new BigDecimal("32"), MATH_CONTEXT);
            case "K" -> celsius.add(new BigDecimal("273.15"), MATH_CONTEXT);
            default -> throw new IllegalArgumentException("不支持的温度单位：" + unit);
        };
    }
}

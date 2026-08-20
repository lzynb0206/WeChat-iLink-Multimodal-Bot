package com.example.demo.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Map;

@Component
public class CalculatorTool implements BotTool {
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;
    private static final BigDecimal MAX_ABSOLUTE_VALUE = new BigDecimal("1E100");

    @Override
    public String name() {
        return "calculate";
    }

    @Override
    public String description() {
        return "执行两个数字的加、减、乘、除精确计算。用户提出明确算术题时使用，不要靠模型心算。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "operation", Map.of(
                                "type", "string",
                                "enum", List.of("add", "subtract", "multiply", "divide"),
                                "description", "运算类型：加、减、乘、除"
                        ),
                        "left", Map.of(
                                "type", "number",
                                "description", "左操作数"
                        ),
                        "right", Map.of(
                                "type", "number",
                                "description", "右操作数"
                        )
                ),
                "required", List.of("operation", "left", "right"),
                "additionalProperties", false
        );
    }

    @Override
    public String execute(JsonNode arguments) {
        requireNumber(arguments, "left");
        requireNumber(arguments, "right");
        String operation = arguments.path("operation").asText();
        BigDecimal left = arguments.path("left").decimalValue();
        BigDecimal right = arguments.path("right").decimalValue();
        requireSupportedRange(left, "left");
        requireSupportedRange(right, "right");
        BigDecimal result = switch (operation) {
            case "add" -> left.add(right, MATH_CONTEXT);
            case "subtract" -> left.subtract(right, MATH_CONTEXT);
            case "multiply" -> left.multiply(right, MATH_CONTEXT);
            case "divide" -> divide(left, right);
            default -> throw new IllegalArgumentException("不支持的运算类型：" + operation);
        };
        return result.stripTrailingZeros().toPlainString();
    }

    private BigDecimal divide(BigDecimal left, BigDecimal right) {
        if (right.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("除数不能为 0");
        }
        return left.divide(right, MATH_CONTEXT);
    }

    private void requireNumber(JsonNode arguments, String field) {
        if (!arguments.has(field) || !arguments.path(field).isNumber()) {
            throw new IllegalArgumentException("计算工具缺少数字参数：" + field);
        }
    }

    private void requireSupportedRange(BigDecimal value, String field) {
        if (value.abs().compareTo(MAX_ABSOLUTE_VALUE) > 0) {
            throw new IllegalArgumentException("计算参数超出范围：" + field);
        }
    }
}

package com.example.demo.tool;

import com.example.demo.model.WeatherInfo;
import com.example.demo.service.weather.WeatherService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WeatherTool implements BotTool {
    private final WeatherService weatherService;
    private final ObjectMapper objectMapper;

    public WeatherTool(WeatherService weatherService) {
        this.weatherService = weatherService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String name() {
        return "get_current_weather";
    }

    @Override
    public String description() {
        return "查询中国或海外指定城市、区县的当前实况天气和温度。用户询问天气、气温或是否下雨时使用。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "location", Map.of(
                                "type", "string",
                                "description", "最具体、可独立查询的城市或区县名，例如张家港、浦东新区、北京"
                        )
                ),
                "required", List.of("location"),
                "additionalProperties", false
        );
    }

    @Override
    public String execute(JsonNode arguments) {
        String location = arguments.path("location").asText().trim();
        if (!StringUtils.hasText(location)) {
            throw new IllegalArgumentException("天气工具缺少 location 参数");
        }
        WeatherInfo weather = weatherService.getCurrentWeather(location);
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("location", weather.location());
            result.put("weather", weather.weather());
            result.put("temperature_celsius", new BigDecimal(weather.temperature()));
            result.put("last_update", weather.lastUpdate() == null ? "" : weather.lastUpdate().toString());
            result.put("source", "心知天气");
            return objectMapper.writeValueAsString(result);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("天气接口返回的温度不是有效数字", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成天气工具结果", exception);
        }
    }
}

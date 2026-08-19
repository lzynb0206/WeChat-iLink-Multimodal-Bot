package com.example.demo.tool;

import com.example.demo.service.weather.WeatherService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Component
public class WeatherTool implements BotTool {
    private final WeatherService weatherService;

    public WeatherTool(WeatherService weatherService) {
        this.weatherService = weatherService;
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
        return weatherService.getCurrentWeather(location).toReplyText();
    }
}

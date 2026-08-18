package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "weather")
public class WeatherConfig {
    private String apiKey;
    private String apiUrl = "https://api.seniverse.com/v3/weather/now.json";
}

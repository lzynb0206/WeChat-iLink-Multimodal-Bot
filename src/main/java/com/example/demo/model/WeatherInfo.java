package com.example.demo.model;

import java.time.OffsetDateTime;

public record WeatherInfo(
        String location,
        String weather,
        String temperature,
        OffsetDateTime lastUpdate) {

    public String toReplyText() {
        String updateTime = lastUpdate == null
                ? "未知"
                : lastUpdate.toLocalDateTime().toString().replace('T', ' ');
        return "%s当前天气：%s，气温%s℃。数据更新时间：%s（数据来源：心知天气）"
                .formatted(location, weather, temperature, updateTime);
    }
}

package com.example.demo.model;

public record MessageRouteResult(
        MessageRouteType routeType,
        ActionType action,
        ReplyMode replyMode,
        String content,
        String routeDetail) {
}

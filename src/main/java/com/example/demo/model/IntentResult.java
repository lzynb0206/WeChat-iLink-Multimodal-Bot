package com.example.demo.model;

public record IntentResult(
        ActionType action,
        ReplyMode replyMode,
        String content,
        String location) {
}

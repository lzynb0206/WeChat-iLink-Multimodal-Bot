package com.example.demo.rag;

import java.util.List;

public record KnowledgeDocument(
        String id,
        String title,
        List<String> keywords,
        String content) {
}

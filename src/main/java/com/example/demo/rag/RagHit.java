package com.example.demo.rag;

public record RagHit(
        KnowledgeDocument document,
        int score) {
}

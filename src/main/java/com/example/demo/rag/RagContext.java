package com.example.demo.rag;

import java.util.List;

public record RagContext(List<RagHit> hits) {
    public RagContext {
        hits = List.copyOf(hits);
    }
}

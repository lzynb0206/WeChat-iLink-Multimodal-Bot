package com.example.demo.rag;

import com.example.demo.config.RagConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeywordRagServiceTests {
    @Test
    void sameQuestionMatchesWhenEnabledAndMissesWhenDisabled() {
        RagConfig config = new RagConfig();
        KeywordRagService service = new KeywordRagService(config);

        assertTrue(service.retrieve("RAG是什么，它有什么作用？").isPresent());

        config.setEnabled(false);
        assertFalse(service.retrieve("RAG是什么，它有什么作用？").isPresent());
    }

    @Test
    void buildsPromptWithRetrievedKnowledgeAndOriginalQuestion() {
        KeywordRagService service = new KeywordRagService(new RagConfig());
        RagContext context = service.retrieve("微信语音为什么需要SILK转WAV？").orElseThrow();

        String prompt = service.buildAugmentedPrompt(
                "微信语音为什么需要SILK转WAV？", context);

        assertTrue(prompt.contains("微信语音处理链路"));
        assertTrue(prompt.contains("silk-wasm"));
        assertTrue(prompt.contains("<user_question>"));
    }

    @Test
    void unrelatedQuestionDoesNotCreateContext() {
        KeywordRagService service = new KeywordRagService(new RagConfig());

        assertFalse(service.retrieve("给我讲一个睡前故事").isPresent());
    }
}

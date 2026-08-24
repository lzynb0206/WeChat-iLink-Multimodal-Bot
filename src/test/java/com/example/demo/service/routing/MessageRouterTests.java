package com.example.demo.service.routing;

import com.example.demo.config.AiConfig;
import com.example.demo.config.RagConfig;
import com.example.demo.model.ActionType;
import com.example.demo.model.IntentResult;
import com.example.demo.model.MessageRouteResult;
import com.example.demo.model.MessageRouteType;
import com.example.demo.model.ReplyMode;
import com.example.demo.rag.KeywordRagService;
import com.example.demo.service.ai.AlibabaAiService;
import com.example.demo.skill.BotSkill;
import com.example.demo.skill.SkillRegistry;
import com.example.demo.tool.ToolCallingEngine;
import com.example.demo.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageRouterTests {
    @Test
    void skillHasPriorityOverRagAndLlm() {
        StubAiService aiService = new StubAiService();
        MessageRouter router = router(
                new RagConfig(),
                List.of(new FixedSkill("每日简报", "Skill直接回复")),
                aiService);

        MessageRouteResult result = router.route(
                "生成每日简报，并介绍一下RAG", ReplyMode.TEXT);

        assertEquals(MessageRouteType.SKILL, result.routeType());
        assertEquals("Skill直接回复", result.content());
        assertEquals(0, aiService.intentCalls.get());
        assertEquals(0, aiService.chatCalls.get());
    }

    @Test
    void ragEnhancesPromptBeforeCallingLlm() {
        StubAiService aiService = new StubAiService();
        MessageRouter router = router(new RagConfig(), List.of(), aiService);

        MessageRouteResult result = router.route("RAG是什么？", ReplyMode.TEXT);

        assertEquals(MessageRouteType.RAG, result.routeType());
        assertEquals(0, aiService.intentCalls.get());
        assertEquals(1, aiService.chatCalls.get());
        assertTrue(aiService.lastPrompt.contains("<retrieved_knowledge>"));
        assertTrue(aiService.lastPrompt.contains("RAG 是 Retrieval-Augmented Generation"));
    }

    @Test
    void disabledRagFallsBackToDirectLlmRoute() {
        RagConfig config = new RagConfig();
        config.setEnabled(false);
        StubAiService aiService = new StubAiService();
        MessageRouter router = router(config, List.of(), aiService);

        MessageRouteResult result = router.route("RAG是什么？", ReplyMode.TEXT);

        assertEquals(MessageRouteType.LLM, result.routeType());
        assertEquals(1, aiService.intentCalls.get());
        assertEquals(1, aiService.chatCalls.get());
        assertFalse(aiService.lastPrompt.contains("<retrieved_knowledge>"));
        assertEquals("RAG是什么？", aiService.lastPrompt);
    }

    @Test
    void unmatchedImageRequestKeepsOriginalImageGenerationRoute() {
        StubAiService aiService = new StubAiService();
        aiService.nextIntent = new IntentResult(
                ActionType.IMAGE_GENERATION, ReplyMode.TEXT, "雨中的西湖", "");
        MessageRouter router = router(new RagConfig(), List.of(), aiService);

        MessageRouteResult result = router.route("生成一张雨中的西湖", ReplyMode.TEXT);

        assertEquals(MessageRouteType.LLM, result.routeType());
        assertEquals(ActionType.IMAGE_GENERATION, result.action());
        assertEquals("雨中的西湖", result.content());
        assertEquals(0, aiService.chatCalls.get());
    }

    private MessageRouter router(
            RagConfig ragConfig,
            List<BotSkill> skills,
            StubAiService aiService) {
        return new MessageRouter(
                new SkillRegistry(skills),
                new KeywordRagService(ragConfig),
                aiService);
    }

    private record FixedSkill(String keyword, String reply) implements BotSkill {
        @Override
        public String name() {
            return "fixed_skill";
        }

        @Override
        public String description() {
            return "固定回复测试Skill";
        }

        @Override
        public List<String> keywords() {
            return List.of(keyword);
        }

        @Override
        public String execute(String userMessage) {
            return reply;
        }
    }

    private static class StubAiService extends AlibabaAiService {
        private final AtomicInteger intentCalls = new AtomicInteger();
        private final AtomicInteger chatCalls = new AtomicInteger();
        private IntentResult nextIntent;
        private String lastPrompt = "";

        StubAiService() {
            super(new AiConfig(), new ToolCallingEngine(new ToolRegistry(List.of())));
        }

        @Override
        public IntentResult recognizeIntent(String text, ReplyMode defaultReplyMode) {
            intentCalls.incrementAndGet();
            return nextIntent == null
                    ? new IntentResult(ActionType.CHAT, defaultReplyMode, text, "")
                    : nextIntent;
        }

        @Override
        public String chatWithTools(String userPrompt) {
            chatCalls.incrementAndGet();
            lastPrompt = userPrompt;
            return "LLM回复";
        }
    }
}

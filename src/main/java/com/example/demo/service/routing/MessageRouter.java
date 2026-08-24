package com.example.demo.service.routing;

import com.example.demo.model.ActionType;
import com.example.demo.model.IntentResult;
import com.example.demo.model.MessageRouteResult;
import com.example.demo.model.MessageRouteType;
import com.example.demo.model.ReplyMode;
import com.example.demo.rag.KeywordRagService;
import com.example.demo.rag.RagContext;
import com.example.demo.service.ai.AlibabaAiService;
import com.example.demo.skill.SkillExecution;
import com.example.demo.skill.SkillRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
public class MessageRouter {
    private final SkillRegistry skillRegistry;
    private final KeywordRagService ragService;
    private final AlibabaAiService aiService;

    public MessageRouter(
            SkillRegistry skillRegistry,
            KeywordRagService ragService,
            AlibabaAiService aiService) {
        this.skillRegistry = skillRegistry;
        this.ragService = ragService;
        this.aiService = aiService;
    }

    public MessageRouteResult route(String userMessage, ReplyMode defaultReplyMode) {
        if (!StringUtils.hasText(userMessage)) {
            throw new IllegalArgumentException("消息内容不能为空");
        }

        ReplyMode directReplyMode = resolveDirectReplyMode(userMessage, defaultReplyMode);
        Optional<SkillExecution> skillExecution = skillRegistry.route(userMessage);
        if (skillExecution.isPresent()) {
            SkillExecution execution = skillExecution.get();
            log.info("消息路由命中Skill skill={}", execution.skillName());
            return new MessageRouteResult(
                    MessageRouteType.SKILL,
                    ActionType.CHAT,
                    directReplyMode,
                    execution.reply(),
                    execution.skillName());
        }

        Optional<RagContext> ragContext = ragService.retrieve(userMessage);
        if (ragContext.isPresent()) {
            String augmentedPrompt = ragService.buildAugmentedPrompt(
                    userMessage, ragContext.get());
            String reply = aiService.chatWithTools(augmentedPrompt);
            String documentIds = ragContext.get().hits().stream()
                    .map(hit -> hit.document().id())
                    .reduce((left, right) -> left + "," + right)
                    .orElse("");
            log.info("消息路由命中RAG documents={}", documentIds);
            return new MessageRouteResult(
                    MessageRouteType.RAG,
                    ActionType.CHAT,
                    directReplyMode,
                    reply,
                    documentIds);
        }

        IntentResult intent = aiService.recognizeIntent(userMessage, defaultReplyMode);
        if (intent.action() == ActionType.IMAGE_GENERATION) {
            log.info("消息路由进入LLM生图意图");
            return new MessageRouteResult(
                    MessageRouteType.LLM,
                    ActionType.IMAGE_GENERATION,
                    ReplyMode.TEXT,
                    intent.content(),
                    "image_generation");
        }

        String prompt = intent.action() == ActionType.WEATHER
                ? "请查询“" + intent.location() + "”的当前天气。用户原始问题：" + intent.content()
                : intent.content();
        String reply = aiService.chatWithTools(prompt);
        log.info("消息路由进入LLM兜底 action={}", intent.action());
        return new MessageRouteResult(
                MessageRouteType.LLM,
                ActionType.CHAT,
                intent.replyMode(),
                reply,
                intent.action().name().toLowerCase(Locale.ROOT));
    }

    private ReplyMode resolveDirectReplyMode(String message, ReplyMode fallback) {
        String lower = message.toLowerCase(Locale.ROOT);
        ReplyMode result = lower.contains("语音") || lower.contains("朗读")
                || lower.contains("说出来") || lower.contains("读出来")
                ? ReplyMode.VOICE
                : fallback;
        if (lower.contains("文字回复") || lower.contains("用文字") || lower.contains("打字")) {
            result = ReplyMode.TEXT;
        }
        return result;
    }
}

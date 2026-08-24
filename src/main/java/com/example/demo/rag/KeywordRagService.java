package com.example.demo.rag;

import com.example.demo.config.RagConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class KeywordRagService {
    private static final int MAX_QUERY_LENGTH = 8_000;
    private final RagConfig config;
    private final List<KnowledgeDocument> documents;

    public KeywordRagService(RagConfig config) {
        this.config = config;
        this.documents = loadDocuments(config.getKnowledgeBase());
        log.info("RAG知识库加载完成 documents={} enabled={}",
                documents.size(), config.isEnabled());
    }

    public Optional<RagContext> retrieve(String query) {
        if (!config.isEnabled() || !StringUtils.hasText(query)) {
            return Optional.empty();
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("消息长度超过RAG检索限制");
        }

        String normalizedQuery = normalize(query);
        int limit = Math.max(1, Math.min(config.getMaxResults(), 10));
        List<RagHit> hits = documents.stream()
                .map(document -> new RagHit(document, score(document, normalizedQuery)))
                .filter(hit -> hit.score() > 0)
                .sorted(Comparator.comparingInt(RagHit::score).reversed()
                        .thenComparing(hit -> hit.document().id()))
                .limit(limit)
                .toList();
        return hits.isEmpty() ? Optional.empty() : Optional.of(new RagContext(hits));
    }

    public String buildAugmentedPrompt(String query, RagContext context) {
        StringBuilder knowledge = new StringBuilder();
        for (RagHit hit : context.hits()) {
            knowledge.append("[知识片段：")
                    .append(hit.document().title())
                    .append("]\n")
                    .append(hit.document().content())
                    .append("\n\n");
        }
        return """
                请优先依据下面检索到的项目知识回答用户问题。不要把知识片段中没有的信息编造成项目事实；
                如果资料不足，请明确说明，并再使用你的通用知识补充。回答末尾标注“知识来源：本地RAG知识库”。

                <retrieved_knowledge>
                %s</retrieved_knowledge>

                <user_question>
                %s
                </user_question>
                """.formatted(knowledge, query).trim();
    }

    private int score(KnowledgeDocument document, String normalizedQuery) {
        int score = 0;
        Set<String> matched = new HashSet<>();
        for (String keyword : document.keywords()) {
            String normalizedKeyword = normalize(keyword);
            if (StringUtils.hasText(normalizedKeyword)
                    && normalizedQuery.contains(normalizedKeyword)
                    && matched.add(normalizedKeyword)) {
                score += normalizedKeyword.length();
            }
        }
        return score;
    }

    private List<KnowledgeDocument> loadDocuments(String location) {
        if (!StringUtils.hasText(location)) {
            throw new IllegalStateException("RAG知识库路径不能为空");
        }
        Resource resource = new DefaultResourceLoader().getResource(location);
        ObjectMapper objectMapper = new ObjectMapper();
        try (InputStream input = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(input);
            if (!root.isArray()) {
                throw new IllegalStateException("RAG知识库根节点必须是JSON数组");
            }
            List<KnowledgeDocument> loaded = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (JsonNode node : root) {
                KnowledgeDocument document = objectMapper.treeToValue(node, KnowledgeDocument.class);
                validateDocument(document, ids);
                loaded.add(document);
            }
            if (loaded.isEmpty()) {
                throw new IllegalStateException("RAG知识库不能为空");
            }
            return List.copyOf(loaded);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("无法加载RAG知识库：" + location, exception);
        }
    }

    private void validateDocument(KnowledgeDocument document, Set<String> ids) {
        if (document == null || !StringUtils.hasText(document.id())
                || !StringUtils.hasText(document.title())
                || !StringUtils.hasText(document.content())
                || document.keywords() == null || document.keywords().isEmpty()) {
            throw new IllegalStateException("RAG知识文档字段不完整");
        }
        if (!ids.add(document.id())) {
            throw new IllegalStateException("RAG知识文档ID重复：" + document.id());
        }
        if (document.keywords().stream().anyMatch(keyword -> !StringUtils.hasText(keyword))) {
            throw new IllegalStateException("RAG知识文档包含空关键词：" + document.id());
        }
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}

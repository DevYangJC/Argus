package com.argus.rag.qa.service;

import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.qa.model.QueryPlanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 查询规划服务。
 * <p>
 * 调用大模型分析用户问题，决定采用哪种检索策略（直接、重写或分解），
 * 并生成对应的检索语句列表。规划失败时自动回退为 DIRECT 策略。
 * </p>
 */
@Service
public class QueryPlanningService {

    private static final Logger log = LoggerFactory.getLogger(QueryPlanningService.class);

    /** 检索语句最大数量限制 */
    private static final int MAX_QUERY_COUNT = 3;

    private final ChatClient queryPlanningChatClient;
    private final PromptTemplate queryPlanningUserPromptTemplate;

    /**
     * 构造函数。
     *
     * @param queryPlanningChatClient         查询规划专用的 ChatClient
     * @param queryPlanningUserPromptTemplate 查询规划用户提示词模板
     */
    public QueryPlanningService(
            @Qualifier("queryPlanningChatClient") ChatClient queryPlanningChatClient,
            @Qualifier("queryPlanningUserPromptTemplate") PromptTemplate queryPlanningUserPromptTemplate
    ) {
        this.queryPlanningChatClient = queryPlanningChatClient;
        this.queryPlanningUserPromptTemplate = queryPlanningUserPromptTemplate;
    }

    /**
     * 对用户问题执行查询规划。
     * <p>
     * 调用大模型分析问题并返回检索策略和检索语句，
     * 失败时回退为 DIRECT 策略。
     * </p>
     *
     * @param question 用户原始问题
     * @return 查询规划结果
     */
    public QueryPlanResult plan(String question) {
        String normalizedQuestion = requireQuestion(question);
        try {

            Prompt planPrompt = queryPlanningUserPromptTemplate.create(Map.of("question", normalizedQuestion));

            QueryPlanResult rawResult = queryPlanningChatClient.prompt(planPrompt)
//                    .user(user -> user.text(renderUserPrompt(normalizedQuestion)))
                    .call()
                    .entity(QueryPlanResult.class);
            return validatePlan(rawResult, normalizedQuestion);
        } catch (RuntimeException exception) {
            log.warn("Query planning failed, fallback to direct query. question={}", normalizedQuestion, exception);
            return QueryPlanResult.fallback(normalizedQuestion);
        }
    }

    /**
     * 校验规划结果，确保策略和检索语句有效。
     * 根据不同策略构建最终的检索语句列表。
     */
    private QueryPlanResult validatePlan(QueryPlanResult rawResult, String originalQuestion) {
        if (rawResult == null || rawResult.strategy() == null) {
            return QueryPlanResult.fallback(originalQuestion);
        }
        Set<String> normalizedQueries = normalizeQueries(rawResult.queries());
        if (normalizedQueries.isEmpty()) {
            return QueryPlanResult.fallback(originalQuestion);
        }
        List<String> finalQueries = switch (rawResult.strategy()) {
            case DIRECT -> List.of(originalQuestion);
            case REWRITE -> buildRewriteQueries(originalQuestion, normalizedQueries);
            case DECOMPOSE -> limitQueries(normalizedQueries);
        };
        if (finalQueries.isEmpty()) {
            return QueryPlanResult.fallback(originalQuestion);
        }
        return new QueryPlanResult(rawResult.strategy(), finalQueries);
    }

    /** 构建 REWRITE 策略的检索语句：原始问题 + 重写后的语句 */
    private List<String> buildRewriteQueries(String originalQuestion, Set<String> normalizedQueries) {
        LinkedHashSet<String> rewriteQueries = new LinkedHashSet<>();
        rewriteQueries.add(originalQuestion);
        rewriteQueries.addAll(normalizedQueries);
        return limitQueries(rewriteQueries);
    }

    /** 规范化检索语句：去除空白、去重 */
    private Set<String> normalizeQueries(List<String> queries) {
        LinkedHashSet<String> normalizedQueries = new LinkedHashSet<>();
        if (queries == null) {
            return normalizedQueries;
        }
        for (String query : queries) {
            if (!StringUtils.hasText(query)) {
                continue;
            }
            String normalized = query.replaceAll("\\s+", " ").trim();
            if (StringUtils.hasText(normalized)) {
                normalizedQueries.add(normalized);
            }
        }
        return normalizedQueries;
    }

    /** 限制检索语句数量不超过 {@link #MAX_QUERY_COUNT} */
    private List<String> limitQueries(Set<String> queries) {
        return queries.stream()
                .limit(MAX_QUERY_COUNT)
                .toList();
    }

    /** 渲染用户提示词模板（当前未使用，保留备用） */
    private String renderUserPrompt(String question) {
        return queryPlanningUserPromptTemplate.render(Map.of("question", question));
    }

    /** 校验问题非空并规范化空白字符 */
    private String requireQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            throw new BusinessException("问题不能为空");
        }
        return question.replaceAll("\\s+", " ").trim();
    }
}

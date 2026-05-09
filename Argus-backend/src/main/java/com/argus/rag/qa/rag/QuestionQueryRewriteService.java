package com.argus.rag.qa.rag;

import com.argus.rag.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 问题查询重写服务。
 * <p>
 * 基于规则的问题重写策略，将原始问题扩展为多个检索语句：
 * 包括原始问题、简化后的问题（去除客套词和疑问词）和按标点拆分的子句。
 * </p>
 */
@Service
public class QuestionQueryRewriteService {

    /** 拆分问题的标点符模式：中英文问号、句号、分号 */
    private static final Pattern SPLIT_PATTERN = Pattern.compile("[？?。；;]");

    /**
     * 将原始问题重写为多个检索语句。
     * <p>
     * 生成顺序：原始问题 → 简化后的问题 → 按标点拆分的子句。
     * 结果自动去重。
     * </p>
     *
     * @param question 用户原始问题
     * @return 重写后的检索语句列表
     */
    public List<String> rewrite(String question) {
        String normalizedQuestion = requireQuestion(question);
        Set<String> rewrittenQueries = new LinkedHashSet<>();
        rewrittenQueries.add(normalizedQuestion);

        String simplifiedQuestion = simplifyQuestion(normalizedQuestion);
        if (StringUtils.hasText(simplifiedQuestion)) {
            rewrittenQueries.add(simplifiedQuestion);
        }
        for (String splitQuery : splitQuestion(normalizedQuestion)) {
            rewrittenQueries.add(splitQuery);
        }
        return List.copyOf(rewrittenQueries);
    }

    /** 校验问题非空并规范化空白字符 */
    private String requireQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            throw new BusinessException("问题不能为空");
        }
        return question.replaceAll("\\s+", " ").trim();
    }

    /** 简化问题：去除常见客套词和疑问词，若无变化则返回 {@code null} */
    private String simplifyQuestion(String question) {
        String simplified = question
                .replaceFirst("^(请问|请|帮我|麻烦你|麻烦|想知道|我想知道)", "")
                .replaceFirst("(是什么|是啥|有哪些|怎么做|如何处理|如何实现|请说明)$", "")
                .trim();
        return simplified.equals(question) ? null : simplified;
    }

    /** 按标点符拆分问题为多个子句，仅保留长度 ≥ 4 的片段 */
    private List<String> splitQuestion(String question) {
        Set<String> splitQueries = new LinkedHashSet<>();
        for (String fragment : SPLIT_PATTERN.split(question)) {
            String normalized = fragment.trim();
            if (normalized.length() >= 4) {
                splitQueries.add(normalized);
            }
        }
        return List.copyOf(splitQueries);
    }
}

package com.dong.ddrag.qa.rag;

import com.dong.ddrag.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class QuestionQueryRewriteService {

    private static final Pattern SPLIT_PATTERN = Pattern.compile("[？?。；;]");

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

    private String requireQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            throw new BusinessException("问题不能为空");
        }
        return question.replaceAll("\\s+", " ").trim();
    }

    private String simplifyQuestion(String question) {
        String simplified = question
                .replaceFirst("^(请问|请|帮我|麻烦你|麻烦|想知道|我想知道)", "")
                .replaceFirst("(是什么|是啥|有哪些|怎么做|如何处理|如何实现|请说明)$", "")
                .trim();
        return simplified.equals(question) ? null : simplified;
    }

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

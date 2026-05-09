package com.argus.rag.qa.support;

import com.argus.rag.qa.model.KnowledgeAnswerOutput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 问答回答解析器。
 * <p>
 * 当大模型的结构化输出失败时，作为回退方案：
 * 将大模型返回的原始 JSON 文本解析为 {@link KnowledgeAnswerOutput} 对象。
 * </p>
 */
@Component
public class QaAnswerParser {

    private final ObjectMapper objectMapper;

    /**
     * 构造函数。
     *
     * @param objectMapper JSON 序列化/反序列化工具
     */
    public QaAnswerParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析大模型返回的原始 JSON 文本为结构化回答对象。
     *
     * @param rawAnswer 大模型返回的原始文本，应为 JSON 格式
     * @return 解析成功返回 {@link KnowledgeAnswerOutput}，输入为空或解析失败时返回 {@code null}
     */
    public KnowledgeAnswerOutput parse(String rawAnswer) {
        if (!StringUtils.hasText(rawAnswer)) {
            return null;
        }
        try {
            return objectMapper.readValue(rawAnswer, KnowledgeAnswerOutput.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }
}

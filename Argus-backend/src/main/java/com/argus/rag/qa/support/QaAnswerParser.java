package com.dong.ddrag.qa.support;

import com.dong.ddrag.qa.model.KnowledgeAnswerOutput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class QaAnswerParser {

    private final ObjectMapper objectMapper;

    public QaAnswerParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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

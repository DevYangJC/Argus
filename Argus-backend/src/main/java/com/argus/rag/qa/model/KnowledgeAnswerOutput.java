package com.dong.ddrag.qa.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeAnswerOutput(
        boolean answered,
        String answer,
        String reasonCode,
        String reasonMessage
) {
}

package com.dong.ddrag.qa.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QueryPlanResult(
        QueryPlanStrategy strategy,
        List<String> queries
) {

    public static QueryPlanResult fallback(String question) {
        return new QueryPlanResult(QueryPlanStrategy.DIRECT, List.of(question));
    }
}

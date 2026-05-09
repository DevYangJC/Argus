package com.dong.ddrag.qa.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration
public class QueryPlanningConfiguration {

    @Bean("queryPlanningChatClient")
    public ChatClient queryPlanningChatClient(
            ChatClient.Builder chatClientBuilder
    ) {
        return chatClientBuilder.build();
    }

    @Bean("queryPlanningUserPromptTemplate")
    public PromptTemplate queryPlanningUserPromptTemplate() {
        return PromptTemplate.builder()
                .resource(new ClassPathResource("prompts/query-planning/user.st"))
                .build();
    }
}

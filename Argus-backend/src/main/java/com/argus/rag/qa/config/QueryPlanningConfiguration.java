package com.argus.rag.qa.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * 查询规划配置类。
 * <p>
 * 配置查询规划服务所需的大模型客户端和 Prompt 模板。
 * 查询规划服务负责分析用户问题，决定采用直接检索、重写检索或分解检索策略。
 * </p>
 */
@Configuration
public class QueryPlanningConfiguration {

    /**
     * 创建查询规划专用的 ChatClient。
     * <p>使用默认配置，无额外系统提示词和顾问。</p>
     *
     * @param chatClientBuilder Spring AI 提供的 ChatClient 构造器
     * @return 查询规划专用的 ChatClient 实例
     */
    @Bean("queryPlanningChatClient")
    public ChatClient queryPlanningChatClient(
            ChatClient.Builder chatClientBuilder
    ) {
        return chatClientBuilder.build();
    }

    /**
     * 加载查询规划的用户提示词模板。
     * <p>模板文件路径：{@code prompts/query-planning/user.st}，支持 {@code {question}} 变量插值。</p>
     *
     * @return 查询规划用户提示词模板
     */
    @Bean("queryPlanningUserPromptTemplate")
    public PromptTemplate queryPlanningUserPromptTemplate() {
        return PromptTemplate.builder()
                .resource(new ClassPathResource("prompts/query-planning/user.st"))
                .build();
    }
}

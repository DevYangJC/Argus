package com.argus.rag.assistant.memory;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * 助手记忆模块 Prompt 模板配置。
 * <p>定义并注册与记忆相关的 PromptTemplate Bean，包括会话记忆、紧凑摘要和运行时上下文压缩模板。</p>
 */
@Configuration
public class AssistantMemoryPromptConfiguration {

    /**
     * 会话记忆更新 Prompt 模板。
     * <p>用于调用 LLM 基于现有会话记忆和新增消息，生成更新后的会话记忆摘要。</p>
     *
     * @return 会话记忆 PromptTemplate Bean
     */
    @Bean
    @Qualifier("assistantSessionMemoryPromptTemplate")
    public PromptTemplate assistantSessionMemoryPromptTemplate() {
        return PromptTemplate.builder()
                .resource(new ClassPathResource("prompts/assistant/session-memory-update.st"))
                .build();
    }

    /**
     * 紧凑摘要 Prompt 模板。
     * <p>用于调用 LLM 对会话历史进行更精炼的压缩，生成紧凑摘要。</p>
     *
     * @return 紧凑摘要 PromptTemplate Bean
     */
    @Bean
    @Qualifier("assistantCompactSummaryPromptTemplate")
    public PromptTemplate assistantCompactSummaryPromptTemplate() {
        return PromptTemplate.builder()
                .resource(new ClassPathResource("prompts/assistant/session-compact-summary.st"))
                .build();
    }

    /**
     * 运行时压缩 Prompt 模板。
     * <p>用于在模型调用前对上下文进行实时压缩，结合紧凑摘要和当前问题生成精简的运行时上下文。</p>
     *
     * @return 运行时压缩 PromptTemplate Bean
     */
    @Bean
    @Qualifier("assistantRuntimeCompactPromptTemplate")
    public PromptTemplate assistantRuntimeCompactPromptTemplate() {
        return PromptTemplate.builder()
                .resource(new ClassPathResource("prompts/assistant/runtime-compact-summary.st"))
                .build();
    }
}

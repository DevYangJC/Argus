package com.argus.rag.qa.config;

import com.argus.rag.qa.rag.ReadyChunkDocumentRetriever;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * 知识问答 ChatClient 配置类。
 * <p>
 * 配置问答流程所需的大模型客户端、Prompt 模板和 RAG 检索增强顾问。
 * 包括：系统提示词、用户提示词、RAG 上下文提示词和检索增强顾问。
 * </p>
 */
@Configuration
public class QaChatClientConfiguration {

    /**
     * 创建问答专用的 ChatClient。
     * <p>
     * 默认配置系统提示词和 RAG 检索增强顾问，
     * 使得每次问答请求自动触发知识库检索并将结果注入上下文。
     * </p>
     *
     * @param chatClientBuilder      Spring AI 提供的 ChatClient 构造器
     * @param qaSystemPromptTemplate 系统提示词模板，定义模型的角色和回答规范
     * @param qaRetrievalAdvisor      RAG 检索增强顾问
     * @return 配置完成的 ChatClient 实例
     */
    @Bean
    public ChatClient qaChatClient(
            ChatClient.Builder chatClientBuilder,
            @Qualifier("qaSystemPromptTemplate") PromptTemplate qaSystemPromptTemplate,
            @Qualifier("qaRetrievalAdvisor") RetrievalAugmentationAdvisor qaRetrievalAdvisor
    ) {
        return chatClientBuilder
                .defaultSystem(qaSystemPromptTemplate.getTemplate())
                .defaultAdvisors(qaRetrievalAdvisor)
                .build();
    }

    /**
     * 加载系统提示词模板，定义大模型的角色和回答规范。
     * <p>模板文件路径：{@code prompts/qa/system.st}</p>
     *
     * @return 系统提示词模板
     */
    @Bean
    public PromptTemplate qaSystemPromptTemplate() {
        return PromptTemplate.builder()
                .resource(new ClassPathResource("prompts/qa/system.st"))
                .build();
    }

    /**
     * 加载用户提示词模板，用于构造发送给大模型的用户消息。
     * <p>模板文件路径：{@code prompts/qa/user.st}，支持变量插值（问题、证据等级等）。</p>
     *
     * @return 用户提示词模板
     */
    @Bean
    public PromptTemplate qaUserPromptTemplate() {
        return PromptTemplate.builder()
                .resource(new ClassPathResource("prompts/qa/user.st"))
                .build();
    }

    /**
     * 加载 RAG 上下文提示词模板，用于将检索到的文档切片注入大模型上下文。
     * <p>模板文件路径：{@code prompts/qa/rag-context.st}</p>
     *
     * @return RAG 上下文提示词模板
     */
    @Bean
    public PromptTemplate qaRagContextPromptTemplate() {
        return PromptTemplate.builder()
                .resource(new ClassPathResource("prompts/qa/rag-context.st"))
                .build();
    }

    /**
     * 配置 RAG 检索增强顾问。
     * <p>
     * 将文档检索器和上下文增强器组装为顾问，
     * 在每次问答时自动检索知识库并将结果注入 Prompt。
     * {@code allowEmptyContext = true} 表示即使无检索结果也不会报错。
     * </p>
     *
     * @param readyChunkDocumentRetriever 文档检索器，负责从知识库检索相关文档切片
     * @param qaRagContextPromptTemplate  RAG 上下文提示词模板
     * @return RAG 检索增强顾问
     */
    @Bean("qaRetrievalAdvisor")
    public RetrievalAugmentationAdvisor qaRetrievalAdvisor(
            ReadyChunkDocumentRetriever readyChunkDocumentRetriever,
            @Qualifier("qaRagContextPromptTemplate") PromptTemplate qaRagContextPromptTemplate
    ) {
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(readyChunkDocumentRetriever)
                .queryAugmenter(new ContextualQueryAugmenter.Builder()
                        .allowEmptyContext(true)
                        .promptTemplate(qaRagContextPromptTemplate)
                        .build())
                .build();
    }
}

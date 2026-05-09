package com.argus.rag.assistant.agent;

import com.argus.rag.qa.model.vo.AskQuestionResponse;

import java.util.List;

/**
 * 知识库检索工具结果持有器。
 * <p>用于在 Agent 图执行过程中跨步骤传递知识库检索的结果和引用信息。</p>
 * <p>主要通过 {@link AssistantKnowledgeBaseTool} 在工具执行完毕后记录引用列表，
 * 后续由 {@link AssistantAgentFacade} 读取并组装最终响应。</p>
 */
public class AssistantKnowledgeBaseToolResultHolder {

    private List<AskQuestionResponse.Citation> citations = List.of();
    private KnowledgeBaseSearchState searchState = KnowledgeBaseSearchState.NOT_STARTED;

    /**
     * 记录知识库检索的引用列表。
     * <p>由 {@link AssistantKnowledgeBaseTool} 在检索完成后调用，
     * 同时将检索状态标记为 {@link KnowledgeBaseSearchState#COMPLETED}。</p>
     *
     * @param citations 引用列表，为 {@code null} 时视为空列表
     */
    public void recordCitations(List<AskQuestionResponse.Citation> citations) {
        this.citations = citations == null ? List.of() : List.copyOf(citations);
        this.searchState = KnowledgeBaseSearchState.COMPLETED;
    }

    /**
     * 获取当前的引用列表。
     * <p>如果尚未执行检索，返回空列表。</p>
     *
     * @return 当前记录的引用列表（不可变）
     */
    public List<AskQuestionResponse.Citation> currentCitations() {
        return citations;
    }

    /**
     * 判断是否已完成知识库检索。
     * <p>用于 {@link AssistantKnowledgeBaseTool} 检测重复调用，
     * 防止 Agent 在同一轮对话中多次检索知识库。</p>
     *
     * @return {@code true} 如果已完成检索
     */
    public boolean hasCompletedSearch() {
        return searchState == KnowledgeBaseSearchState.COMPLETED;
    }

    /**
     * 知识库检索状态枚举。
     * <p>用于追踪一次 Agent 调用周期内知识库检索的完成情况。</p>
     */
    enum KnowledgeBaseSearchState {
        /** 尚未开始检索 */
        NOT_STARTED,
        /** 已完成检索 */
        COMPLETED
    }
}

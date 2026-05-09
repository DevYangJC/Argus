package com.argus.rag.assistant.support.config;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.argus.rag.assistant.model.enums.AssistantToolMode;
import org.springframework.stereotype.Component;

/**
 * 助手 RunnableConfig 工厂。
 * <p>创建 Agent 执行所需的 {@link RunnableConfig}，包含线程ID和用户/会话/工具模式的元数据，
 * 供 Hook 在运行时读取。</p>
 */
@Component
public class AssistantRunnableConfigFactory {

    /**
     * 创建 Agent 执行所需的 {@link RunnableConfig}。
     * <p>构建包含线程 ID 和元数据的配置对象，元数据包括 userId、sessionId、toolMode
     * 和可选的 groupId，供 Hook 在运行时通过 {@code config.metadata(key)} 读取。</p>
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param toolMode  当前工具模式
     * @param groupId   知识库组 ID，可能为 {@code null}
     * @return 填充了元数据的 RunnableConfig 实例
     */
    public RunnableConfig create(
            Long userId,
            Long sessionId,
            AssistantToolMode toolMode,
            Long groupId
    ) {
        String threadId = "user:%d:session:%d".formatted(userId, sessionId);
        RunnableConfig.Builder builder = RunnableConfig.builder()
                .threadId(threadId)
                .addMetadata("userId", userId)
                .addMetadata("sessionId", sessionId)
                .addMetadata("toolMode", toolMode == null ? null : toolMode.name());
        if (groupId != null) {
            builder.addMetadata("groupId", groupId);
        }
        return builder.build();
    }
}

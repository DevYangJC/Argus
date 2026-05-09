package com.argus.rag.assistant.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AssistantMessageEntity {

    /**
     * 消息ID，主键，自动生成
     */
    private Long id;
    /**
     * 所属会话ID
     * <p>必填，关联 {@link AssistantSessionEntity} 的主键</p>
     */
    private Long sessionId;
    /**
     * 消息角色，标识发送者类型
     * <p>对应枚举 {@link com.argus.rag.assistant.model.enums.AssistantMessageRole} 的 name()</p>
     */
    private String role;
    /**
     * 消息产生时的工具模式
     * <p>对应枚举 {@link com.argus.rag.assistant.model.enums.AssistantToolMode} 的 name()</p>
     */
    private String toolMode;
    /**
     * 知识库组ID
     * <p>仅当 toolMode 为 KB_SEARCH 时有值，CHAT 模式下为 null</p>
     */
    private Long groupId;
    /**
     * 消息文本内容
     * <p>必填，不能为空或空白字符串</p>
     */
    private String content;
    /**
     * 结构化负载数据（JSON格式）
     * <p>选填，存储工具调用结果等结构化信息的 JSON 字符串</p>
     */
    private String structuredPayload;
    /**
     * 消息创建时间
     * <p>必填，由系统自动设置</p>
     */
    private LocalDateTime createdAt;

}

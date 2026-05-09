package com.argus.rag.assistant.model.entity;

import lombok.Data;

import java.time.LocalDateTime;
@Data
public class AssistantSessionEntity {

    /**
     * 会话ID，主键，自动生成
     */
    private Long id;
    /**
     * 所属用户ID
     * <p>必填，关联用户表的主键，标识该会话的所有者</p>
     */
    private Long userId;
    /**
     * 会话标题
     * <p>必填，创建时默认为"新会话"，可手动重命名</p>
     */
    private String title;
    /**
     * 会话状态
     * <p>对应枚举 {@link com.argus.rag.assistant.model.enums.AssistantSessionStatus} 的 name()</p>
     */
    private String status;
    /**
     * 最后消息时间
     * <p>选填，会话中最后一条消息的发送时间，用于排序展示</p>
     */
    private LocalDateTime lastMessageAt;
    /**
     * 会话创建时间
     * <p>必填，由系统自动设置</p>
     */
    private LocalDateTime createdAt;
    /**
     * 会话更新时间
     * <p>必填，由系统自动设置，每次更新时刷新</p>
     */
    private LocalDateTime updatedAt;
}

package com.argus.rag.assistant.mapper;

import com.argus.rag.assistant.model.entity.AssistantMessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 助手消息数据访问接口。
 * <p>提供消息实体的 CRUD 操作，包括消息插入、按会话查询消息、统计消息数量等。</p>
 */
@Mapper
public interface AssistantMessageMapper {

    /**
     * 插入一条新的消息记录
     *
     * @param assistantMessageEntity 消息实体，不能为 null
     * @return 受影响的行数，成功应返回 1
     */
    int insert(AssistantMessageEntity assistantMessageEntity);

    /**
     * 统计指定会话中的消息总数
     *
     * @param sessionId 会话ID，不能为空
     * @return 消息总数，若会话不存在返回 0
     */
    Long countBySessionId(@Param("sessionId") Long sessionId);

    /**
     * 查询指定会话的所有消息，按创建时间升序排列
     *
     * @param sessionId 会话ID，不能为空
     * @return 消息实体列表，按创建时间升序
     */
    List<AssistantMessageEntity> selectBySessionIdOrderByCreatedAt(@Param("sessionId") Long sessionId);

    /**
     * 查询指定会话最近 N 条消息，按创建时间降序排列
     *
     * @param sessionId 会话ID，不能为空
     * @param limit     返回的最大消息条数，必须为正整数
     * @return 最近的消息实体列表
     */
    List<AssistantMessageEntity> selectRecentBySessionId(
            @Param("sessionId") Long sessionId,
            @Param("limit") int limit
    );

    /**
     * 删除指定会话的所有消息记录
     *
     * @param sessionId 会话ID，不能为空
     * @return 被删除的消息记录数
     */
    int deleteBySessionId(@Param("sessionId") Long sessionId);
}

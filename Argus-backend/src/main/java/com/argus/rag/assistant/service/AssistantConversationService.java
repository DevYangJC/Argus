package com.argus.rag.assistant.service;

import com.argus.rag.assistant.mapper.AssistantMessageMapper;
import com.argus.rag.assistant.mapper.AssistantSessionContextMapper;
import com.argus.rag.assistant.mapper.AssistantSessionMapper;
import com.argus.rag.assistant.memory.AssistantSessionSummaryService;
import com.argus.rag.assistant.memory.AssistantShortTermMemoryMaintenanceService;
import com.argus.rag.assistant.model.dto.message.AssistantMessageCreateDTO;
import com.argus.rag.assistant.model.entity.AssistantMessageEntity;
import com.argus.rag.assistant.model.entity.AssistantSessionContextEntity;
import com.argus.rag.assistant.model.entity.AssistantSessionEntity;
import com.argus.rag.assistant.model.enums.AssistantMessageRole;
import com.argus.rag.assistant.model.enums.AssistantToolMode;
import com.argus.rag.assistant.model.vo.conversation.AssistantConversationContextVO;
import com.argus.rag.assistant.model.vo.message.AssistantMessageVO;
import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 助手会话管理服务。
 * <p>管理会话消息的持久化、查询、上下文加载等功能。</p>
 * <p>职责包括：消息保存（用户消息和助手消息）、最近消息加载、会话上下文构建、
 * 短期记忆维护触发等。</p>
 */
@Service
public class AssistantConversationService {

    private static final int MAX_RECENT_MESSAGE_LIMIT = 100;

    private final AssistantMessageMapper assistantMessageMapper;
    private final AssistantSessionContextMapper assistantSessionContextMapper;
    private final AssistantSessionMapper assistantSessionMapper;
    private final AssistantSessionSummaryService assistantSessionSummaryService;
    private final AssistantShortTermMemoryMaintenanceService assistantShortTermMemoryMaintenanceService;
    private final AssistantSessionService assistantSessionService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    public AssistantConversationService(
            AssistantMessageMapper assistantMessageMapper,
            AssistantSessionContextMapper assistantSessionContextMapper,
            AssistantSessionMapper assistantSessionMapper,
            AssistantSessionSummaryService assistantSessionSummaryService,
            AssistantShortTermMemoryMaintenanceService assistantShortTermMemoryMaintenanceService,
            AssistantSessionService assistantSessionService,
            CurrentUserService currentUserService,
            ObjectMapper objectMapper
    ) {
        this.assistantMessageMapper = assistantMessageMapper;
        this.assistantSessionContextMapper = assistantSessionContextMapper;
        this.assistantSessionMapper = assistantSessionMapper;
        this.assistantSessionSummaryService = assistantSessionSummaryService;
        this.assistantShortTermMemoryMaintenanceService = assistantShortTermMemoryMaintenanceService;
        this.assistantSessionService = assistantSessionService;
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存用户消息。
     * <p>将用户发送的消息持久化为 {@code USER} 角色的消息记录，并触发短期记忆维护。</p>
     *
     * @param currentUserId 当前登录用户ID
     * @param dto           消息创建请求体，包含会话ID、工具模式、消息内容等
     * @return 保存后的消息视图对象
     * @throws BusinessException 如果会话不存在、参数校验失败或消息保存失败
     */
    @Transactional
    public AssistantMessageVO saveUserMessage(Long currentUserId, AssistantMessageCreateDTO dto) {
        return saveMessage(currentUserId, dto, AssistantMessageRole.USER);
    }

    /**
     * 保存助手回复消息。
     * <p>将 Agent 生成的回复内容持久化为 {@code ASSISTANT} 角色的消息记录，并触发短期记忆维护。</p>
     *
     * @param currentUserId 当前登录用户ID
     * @param dto           消息创建请求体，包含会话ID、工具模式、回复内容等
     * @return 保存后的消息视图对象
     * @throws BusinessException 如果会话不存在、参数校验失败或消息保存失败
     */
    @Transactional
    public AssistantMessageVO saveAssistantMessage(Long currentUserId, AssistantMessageCreateDTO dto) {
        return saveMessage(currentUserId, dto, AssistantMessageRole.ASSISTANT);
    }

    /**
     * 加载指定会话的最近消息列表。
     * <p>按创建时间升序返回最近的 N 条消息，用于构建对话上下文。
     * {@code limit} 会被规范化到最大值 {@value #MAX_RECENT_MESSAGE_LIMIT} 以内。</p>
     *
     * @param currentUserId 当前登录用户ID，用于校验会话归属权
     * @param sessionId     目标会话ID
     * @param limit         要加载的消息数量，取值范围 [1, {@value #MAX_RECENT_MESSAGE_LIMIT}]
     * @return 按时间升序排列的消息视图对象列表
     * @throws BusinessException 如果会话不存在或参数非法
     */
    public List<AssistantMessageVO> loadRecentMessages(Long currentUserId, Long sessionId, int limit) {
        AssistantSessionEntity session = requireOwnedSession(requireUserId(currentUserId), requireSessionId(sessionId));
        int safeLimit = normalizeLimit(limit);
        return assistantMessageMapper.selectRecentBySessionId(session.getId(), safeLimit).stream()
                .sorted(Comparator.comparing(AssistantMessageEntity::getCreatedAt).thenComparing(AssistantMessageEntity::getId))
                .map(this::toMessageVO)
                .toList();
    }

    /**
     * 加载会话的完整对话上下文。
     * <p>构建包含摘要文本、压缩摘要、会话记忆和最近消息的上下文对象。加载策略如下：</p>
     * <ol>
     *   <li>优先使用已有的紧凑摘要和会话记忆；</li>
     *   <li>尝试复用已持久化的摘要；</li>
     *   <li>如果消息量或 token 数超过阈值，则触发摘要生成。</li>
     * </ol>
     *
     * @param currentUserId 当前登录用户ID，用于校验会话归属权
     * @param sessionId     目标会话ID
     * @param recentLimit   要加载的最近消息数量
     * @return 会话对话上下文对象，包含摘要和最近消息
     * @throws BusinessException 如果会话不存在或参数非法
     */
    public AssistantConversationContext loadConversationContext(Long currentUserId, Long sessionId, int recentLimit) {
        AssistantSessionEntity session = requireOwnedSession(requireUserId(currentUserId), requireSessionId(sessionId));
        int safeLimit = normalizeLimit(recentLimit);
        AssistantSessionContextEntity sessionContext = assistantSessionContextMapper.selectBySessionId(session.getId());
        List<AssistantMessageVO> recentMessages = assistantMessageMapper.selectRecentBySessionId(session.getId(), safeLimit).stream()
                .sorted(Comparator.comparing(AssistantMessageEntity::getCreatedAt).thenComparing(AssistantMessageEntity::getId))
                .map(this::toMessageVO)
                .toList();
        String compactSummary = normalizeOptionalText(sessionContext == null ? null : sessionContext.getCompactSummary());
        String sessionMemory = normalizeOptionalText(sessionContext == null ? null : sessionContext.getSessionMemory());
        // 运行时上下文优先复用已沉淀的 summary / compact / session memory，避免每次都把全量消息塞给模型。
        String summaryText = assistantSessionSummaryService.loadReusableSummary(session.getId(), session.getLastMessageAt());
        if (summaryText != null) {
            return new AssistantConversationContext(summaryText, compactSummary, sessionMemory, recentMessages);
        }
        Long totalMessages = assistantMessageMapper.countBySessionId(session.getId());
        List<AssistantMessageEntity> allMessages = assistantMessageMapper.selectBySessionIdOrderByCreatedAt(session.getId());
        int estimatedTokens = assistantSessionSummaryService.estimateTokens(allMessages);
        if (assistantSessionSummaryService.shouldSummarize(
                totalMessages == null ? 0 : totalMessages,
                estimatedTokens,
                session.getLastMessageAt()
        )) {
            String generatedSummary = assistantSessionSummaryService.summarizeAndPersist(session.getId(), allMessages, safeLimit);
            return new AssistantConversationContext(generatedSummary, compactSummary, sessionMemory, recentMessages);
        }
        return new AssistantConversationContext(null, compactSummary, sessionMemory, recentMessages);
    }

    /**
     * 获取会话对话上下文（对外接口）。
     * <p>自动获取当前登录用户身份，加载指定会话的对话上下文，并转为 VO 返回给前端。</p>
     *
     * @param sessionId   目标会话ID
     * @param recentLimit 要加载的最近消息数量
     * @return 对话上下文视图对象，包含摘要文本和最近消息列表
     * @throws BusinessException 如果会话不存在或参数非法
     */
    public AssistantConversationContextVO getConversationContext(
            Long sessionId,
            int recentLimit
    ) {
        Long currentUserId = currentUserService.requireBusinessUser().userId();
        AssistantConversationContext context = loadConversationContext(currentUserId, sessionId, recentLimit);
        return new AssistantConversationContextVO(context.summaryText(), context.recentMessages());
    }

    private AssistantMessageVO saveMessage(
            Long currentUserId,
            AssistantMessageCreateDTO dto,
            AssistantMessageRole role
    ) {
        Long userId = requireUserId(currentUserId);
        AssistantMessageCreateDTO safeDto = requireCreateDTO(dto);
        AssistantSessionEntity session = requireOwnedSession(userId, requireSessionId(safeDto.sessionId()));
        LocalDateTime now = LocalDateTime.now();
        AssistantMessageEntity entity = buildMessageEntity(session.getId(), safeDto, role, now);
        int affectedRows = assistantMessageMapper.insert(entity);
        if (affectedRows != 1 || entity.getId() == null) {
            throw new BusinessException("消息保存失败");
        }
        int updatedRows = assistantSessionMapper.updateLastMessageAt(session.getId(), userId, now);
        if (updatedRows != 1) {
            throw new BusinessException("会话更新时间刷新失败");
        }
        if (role == AssistantMessageRole.USER) {
            Long messageCount = assistantMessageMapper.countBySessionId(session.getId());
            if (messageCount != null && messageCount == 1L) {
                assistantSessionService.autoRenameSessionIfNeeded(userId, session.getId(), safeDto.content());
            }
        }
        // 消息持久化完成后再维护短期记忆，这样短期摘要看到的是数据库里的真实会话状态。
        maintainShortTermMemory(userId, safeDto, role, entity.getId());
        return toMessageVO(entity);
    }

    private AssistantMessageEntity buildMessageEntity(
            Long sessionId,
            AssistantMessageCreateDTO dto,
            AssistantMessageRole role,
            LocalDateTime createdAt
    ) {
        AssistantMessageEntity entity = new AssistantMessageEntity();
        entity.setSessionId(sessionId);
        entity.setRole(role.name());
        entity.setToolMode(dto.toolMode().name());
        entity.setGroupId(normalizeGroupId(dto.toolMode(), dto.groupId()));
        entity.setContent(requireContent(dto.content()));
        entity.setStructuredPayload(normalizeStructuredPayload(dto.structuredPayload()));
        entity.setCreatedAt(createdAt);
        return entity;
    }

    private AssistantSessionEntity requireOwnedSession(Long currentUserId, Long sessionId) {
        AssistantSessionEntity session = assistantSessionMapper.selectByIdAndUserId(sessionId, currentUserId);
        if (session == null) {
            throw new BusinessException("会话不存在");
        }
        return session;
    }

    private AssistantMessageCreateDTO requireCreateDTO(AssistantMessageCreateDTO dto) {
        if (dto == null) {
            throw new BusinessException("消息请求不能为空");
        }
        if (dto.toolMode() == null) {
            throw new BusinessException("toolMode 不能为空");
        }
        return dto;
    }

    private Long requireUserId(Long currentUserId) {
        if (currentUserId == null || currentUserId <= 0) {
            throw new BusinessException("userId 非法");
        }
        return currentUserId;
    }

    private Long requireSessionId(Long sessionId) {
        if (sessionId == null || sessionId <= 0) {
            throw new BusinessException("sessionId 非法");
        }
        return sessionId;
    }

    private String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("content 不能为空");
        }
        return content;
    }

    private String normalizeOptionalText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            throw new BusinessException("limit 非法");
        }
        return Math.min(limit, MAX_RECENT_MESSAGE_LIMIT);
    }

    private Long normalizeGroupId(AssistantToolMode toolMode, Long groupId) {
        if (groupId != null && groupId <= 0) {
            throw new BusinessException("groupId 非法");
        }
        if (toolMode == AssistantToolMode.CHAT) {
            return null;
        }
        if (groupId == null) {
            throw new BusinessException("KB_SEARCH 模式必须提供 groupId");
        }
        return groupId;
    }

    private String normalizeStructuredPayload(String structuredPayload) {
        if (structuredPayload == null || structuredPayload.isBlank()) {
            return null;
        }
        try {
            objectMapper.readTree(structuredPayload);
            return structuredPayload;
        } catch (JsonProcessingException exception) {
            throw new BusinessException("structuredPayload 非法", exception);
        }
    }

    private void maintainShortTermMemory(
            Long userId,
            AssistantMessageCreateDTO dto,
            AssistantMessageRole role,
            Long messageId
    ) {
        if (messageId == null) {
            return;
        }
        if (role == AssistantMessageRole.USER) {
            assistantShortTermMemoryMaintenanceService.maintainBeforeResponse(
                    dto.sessionId(),
                    dto.toolMode(),
                    dto.groupId(),
                    messageId
            );
            return;
        }
        assistantShortTermMemoryMaintenanceService.maintainAfterResponse(
                dto.sessionId(),
                dto.toolMode(),
                dto.groupId(),
                messageId
        );
    }

    private AssistantMessageVO toMessageVO(AssistantMessageEntity entity) {
        return new AssistantMessageVO(
                entity.getId(),
                entity.getSessionId(),
                AssistantMessageRole.valueOf(entity.getRole()),
                entity.getToolMode() == null ? null : AssistantToolMode.valueOf(entity.getToolMode()),
                entity.getGroupId(),
                entity.getContent(),
                entity.getStructuredPayload(),
                entity.getCreatedAt()
        );
    }

    /**
     * 助手对话上下文记录。
     * <p>封装 Agent 调用所需的完整对话上下文信息，包括摘要层和最近消息层，
     * 避免每次请求都将全量历史消息塞入模型上下文窗口。</p>
     *
     * @param summaryText    会话摘要文本，可能为 {@code null}（表示尚无摘要）
     * @param compactSummary 紧凑摘要（短期记忆压缩结果），可能为 {@code null}
     * @param sessionMemory  会话记忆文本，可能为 {@code null}
     * @param recentMessages 最近的 N 条消息列表，按时间升序排列
     */
    public record AssistantConversationContext(
            String summaryText,
            String compactSummary,
            String sessionMemory,
            List<AssistantMessageVO> recentMessages
    ) {
    }
}

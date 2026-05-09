package com.argus.rag.assistant.service;

import com.argus.rag.assistant.mapper.AssistantMessageMapper;
import com.argus.rag.assistant.mapper.AssistantSessionContextMapper;
import com.argus.rag.assistant.mapper.AssistantSessionMapper;
import com.argus.rag.assistant.model.dto.session.UpdateAssistantSessionRequest;
import com.argus.rag.assistant.model.entity.AssistantSessionEntity;
import com.argus.rag.assistant.model.enums.AssistantSessionStatus;
import com.argus.rag.assistant.model.vo.session.AssistantSessionDetailVO;
import com.argus.rag.assistant.model.vo.session.AssistantSessionListItemVO;
import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 助手会话管理服务。
 * <p>提供会话的 CRUD 操作，包括创建会话、查询会话列表、查看详情、重命名、删除会话，
 * 以及根据首条消息自动生成会话标题等功能。</p>
 * <p>所有操作均校验当前用户对目标会话的归属权。</p>
 */
@Service
public class AssistantSessionService {

    private static final String DEFAULT_SESSION_TITLE = "新会话";
    private final AssistantSessionMapper assistantSessionMapper;
    private final AssistantMessageMapper assistantMessageMapper;
    private final AssistantSessionContextMapper assistantSessionContextMapper;
    private final CurrentUserService currentUserService;

    public AssistantSessionService(
            AssistantSessionMapper assistantSessionMapper,
            AssistantMessageMapper assistantMessageMapper,
            AssistantSessionContextMapper assistantSessionContextMapper,
            CurrentUserService currentUserService
    ) {
        this.assistantSessionMapper = assistantSessionMapper;
        this.assistantMessageMapper = assistantMessageMapper;
        this.assistantSessionContextMapper = assistantSessionContextMapper;
        this.currentUserService = currentUserService;
    }

    /**
     * 创建新会话。
     * <p>为当前登录用户创建一个状态为 {@code ACTIVE}、标题为默认值的新会话。</p>
     *
     * @return 新创建会话的详情视图对象
     * @throws BusinessException 如果会话创建失败
     */
    @Transactional
    public AssistantSessionDetailVO createSession() {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser();
        AssistantSessionEntity entity = buildNewSession(currentUser.userId());
        int affectedRows = assistantSessionMapper.insert(entity);
        if (affectedRows != 1 || entity.getId() == null) {
            throw new BusinessException("创建会话失败");
        }
        return toDetailVO(entity);
    }

    /**
     * 查询当前用户的会话列表。
     * <p>按最后消息时间降序排列，返回当前用户的所有会话简要信息。</p>
     *
     * @return 会话列表项视图对象列表，按 {@code lastMessageAt} 降序
     */
    public List<AssistantSessionListItemVO> listSessions() {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser();
        return assistantSessionMapper.selectByUserIdOrderByLastMessageAtDesc(currentUser.userId()).stream()
                .map(this::toListItemVO)
                .toList();
    }

    /**
     * 查询会话详情。
     * <p>校验当前用户对目标会话的归属权后，返回会话的完整信息。</p>
     *
     * @param sessionId 目标会话ID
     * @return 会话详情视图对象，包含标题、状态、时间等完整信息
     * @throws BusinessException 如果会话不存在或 {@code sessionId} 非法
     */
    public AssistantSessionDetailVO getSessionDetail(Long sessionId) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser();
        return toDetailVO(requireOwnedSession(requireSessionId(sessionId), currentUser.userId()));
    }

    /**
     * 重命名会话。
     * <p>校验当前用户对目标会话的归属权后，将会话标题更新为请求中指定的值。
     * 标题会经过规范化处理（去除首尾空格、合并连续空白字符）。</p>
     *
     * @param sessionId   目标会话ID
     * @param requestBody 重命名请求体，包含新标题
     * @return 更新后的会话详情视图对象
     * @throws BusinessException 如果会话不存在、{@code sessionId} 非法、标题为空或更新失败
     */
    @Transactional
    public AssistantSessionDetailVO renameSession(
            Long sessionId,
            UpdateAssistantSessionRequest requestBody
    ) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser();
        AssistantSessionEntity session = requireOwnedSession(requireSessionId(sessionId), currentUser.userId());
        String nextTitle = normalizeTitle(requestBody);
        LocalDateTime now = LocalDateTime.now();
        int updatedRows = assistantSessionMapper.updateTitle(session.getId(), currentUser.userId(), nextTitle, now);
        if (updatedRows != 1) {
            throw new BusinessException("重命名会话失败");
        }
        session.setTitle(nextTitle);
        session.setUpdatedAt(now);
        return toDetailVO(session);
    }

    /**
     * 删除会话。
     * <p>校验当前用户对目标会话的归属权后，级联删除会话上下文、所有消息记录，最后删除会话本身。</p>
     *
     * @param sessionId 目标会话ID
     * @throws BusinessException 如果会话不存在、{@code sessionId} 非法或删除失败
     */
    @Transactional
    public void deleteSession(Long sessionId) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser();
        AssistantSessionEntity session = requireOwnedSession(requireSessionId(sessionId), currentUser.userId());
        assistantSessionContextMapper.deleteBySessionId(session.getId());
        assistantMessageMapper.deleteBySessionId(session.getId());
        int deletedRows = assistantSessionMapper.deleteByIdAndUserId(session.getId(), currentUser.userId());
        if (deletedRows != 1) {
            throw new BusinessException("删除会话失败");
        }
    }

    /**
     * 根据首条用户消息自动为会话生成标题。
     * <p>仅当会话标题仍为默认值（"新会话"）时执行自动命名。提取首条消息的前24个字符作为标题，
     * 并去除换行符和多余空白。如果生成的标题与当前标题相同或为空，则不更新。</p>
     *
     * @param currentUserId     当前用户ID
     * @param sessionId         目标会话ID
     * @param firstUserMessage  用户发送的首条消息内容
     */
    @Transactional
    public void autoRenameSessionIfNeeded(Long currentUserId, Long sessionId, String firstUserMessage) {
        if (currentUserId == null || currentUserId <= 0 || sessionId == null || sessionId <= 0) {
            return;
        }
        AssistantSessionEntity session = requireOwnedSession(sessionId, currentUserId);
        if (!DEFAULT_SESSION_TITLE.equals(session.getTitle())) {
            return;
        }
        String generatedTitle = generateSessionTitle(firstUserMessage);
        if (generatedTitle == null || DEFAULT_SESSION_TITLE.equals(generatedTitle)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int updatedRows = assistantSessionMapper.updateTitle(sessionId, currentUserId, generatedTitle, now);
        if (updatedRows == 1) {
            session.setTitle(generatedTitle);
            session.setUpdatedAt(now);
        }
    }

    private AssistantSessionEntity requireOwnedSession(Long sessionId, Long currentUserId) {
        AssistantSessionEntity entity = assistantSessionMapper.selectByIdAndUserId(sessionId, currentUserId);
        if (entity == null) {
            throw new BusinessException("会话不存在");
        }
        return entity;
    }

    private Long requireSessionId(Long sessionId) {
        if (sessionId == null || sessionId <= 0) {
            throw new BusinessException("sessionId 非法");
        }
        return sessionId;
    }

    private String normalizeTitle(UpdateAssistantSessionRequest requestBody) {
        if (requestBody == null || requestBody.title() == null) {
            throw new BusinessException("title 不能为空");
        }
        String title = requestBody.title().trim().replaceAll("\\s+", " ");
        if (title.isEmpty()) {
            throw new BusinessException("title 不能为空");
        }
        if (title.length() > 255) {
            throw new BusinessException("title 长度不能超过 255");
        }
        return title;
    }

    private String generateSessionTitle(String firstUserMessage) {
        if (firstUserMessage == null) {
            return DEFAULT_SESSION_TITLE;
        }
        String normalized = firstUserMessage
                .replaceAll("\\s+", " ")
                .replace('\n', ' ')
                .trim();
        if (normalized.isEmpty()) {
            return DEFAULT_SESSION_TITLE;
        }
        int maxLength = 24;
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength).trim();
    }

    private AssistantSessionEntity buildNewSession(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        AssistantSessionEntity entity = new AssistantSessionEntity();
        entity.setUserId(userId);
        entity.setTitle(DEFAULT_SESSION_TITLE);
        entity.setStatus(AssistantSessionStatus.ACTIVE.name());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private AssistantSessionListItemVO toListItemVO(AssistantSessionEntity entity) {
        return new AssistantSessionListItemVO(
                entity.getId(),
                entity.getTitle(),
                entity.getLastMessageAt()
        );
    }

    private AssistantSessionDetailVO toDetailVO(AssistantSessionEntity entity) {
        return new AssistantSessionDetailVO(
                entity.getId(),
                entity.getTitle(),
                entity.getStatus(),
                entity.getLastMessageAt(),
                entity.getCreatedAt()
        );
    }
}

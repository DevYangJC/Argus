package com.argus.rag.assistant.memory;

import com.argus.rag.assistant.mapper.AssistantSessionContextMapper;
import com.argus.rag.assistant.model.entity.AssistantMessageEntity;
import com.argus.rag.assistant.model.entity.AssistantSessionContextEntity;
import com.argus.rag.assistant.model.enums.AssistantMessageRole;
import com.argus.rag.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 助手会话摘要服务。
 * <p>管理会话摘要的生成、持久化和复用策略。</p>
 * <p>核心逻辑：</p>
 * <ul>
 *   <li>优先复用以存在的摘要（非过期）</li>
 *   <li>根据消息数、token 估算值和会话活跃度判断是否需要重新生成摘要</li>
 *   <li>摘要文本保留最新的消息并压缩较早的历史消息</li>
 * </ul>
 */
@Service
public class AssistantSessionSummaryService {

    private static final int SUMMARY_SOURCE_CHAR_LIMIT = 2000;
    private static final int TOKEN_ESTIMATE_DIVISOR = 4;

    private final AssistantSessionContextMapper assistantSessionContextMapper;
    private final Clock clock;
    private final int messageThreshold;
    private final int tokenThreshold;
    private final int staleDays;

    @Autowired
    public AssistantSessionSummaryService(
            AssistantSessionContextMapper assistantSessionContextMapper,
            @Value("${assistant.session.summary.message-threshold:20}") int messageThreshold,
            @Value("${assistant.session.summary.token-threshold:8000}") int tokenThreshold,
            @Value("${assistant.session.summary.stale-days:7}") int staleDays
    ) {
        this(assistantSessionContextMapper, Clock.systemDefaultZone(), messageThreshold, tokenThreshold, staleDays);
    }

    public AssistantSessionSummaryService(
            AssistantSessionContextMapper assistantSessionContextMapper,
            Clock clock,
            int messageThreshold,
            int tokenThreshold,
            int staleDays
    ) {
        this.assistantSessionContextMapper = assistantSessionContextMapper;
        this.clock = clock;
        this.messageThreshold = messageThreshold;
        this.tokenThreshold = tokenThreshold;
        this.staleDays = staleDays;
    }

    /**
     * 尝试加载可复用的会话摘要。
     * <p>从数据库查询指定会话的摘要记录，仅在摘要存在、非空且未过期时返回。</p>
     *
     * @param sessionId             会话 ID
     * @param sessionLastMessageAt  会话最后一条消息的时间，用于判断摘要是否过期
     * @return 可复用的摘要文本，若无有效摘要则返回 {@code null}
     */
    public String loadReusableSummary(Long sessionId, LocalDateTime sessionLastMessageAt) {
        AssistantSessionContextEntity existingSummary = assistantSessionContextMapper.selectBySessionId(sessionId);
        if (existingSummary == null || existingSummary.getSummaryText() == null || existingSummary.getSummaryText().isBlank()) {
            return null;
        }
        if (isStale(existingSummary.getUpdatedAt(), sessionLastMessageAt)) {
            return null;
        }
        return existingSummary.getSummaryText();
    }

    /**
     * 判断是否需要生成摘要。
     * <p>根据消息总数、估算 token 数和会话活跃度判断：</p>
     * <ul>
     *   <li>消息数超过阈值或 token 超过阈值时，需要摘要</li>
     *   <li>会话长时间不活跃时，也需要摘要</li>
     * </ul>
     *
     * @param totalMessages         会话消息总数
     * @param estimatedTokens       估算的 token 数
     * @param sessionLastMessageAt  会话最后一条消息的时间
     * @return 需要摘要时返回 {@code true}
     */
    public boolean shouldSummarize(long totalMessages, int estimatedTokens, LocalDateTime sessionLastMessageAt) {
        if (totalMessages > messageThreshold || estimatedTokens > tokenThreshold) {
            return true;
        }
        if (sessionLastMessageAt == null) {
            return false;
        }
        return ChronoUnit.DAYS.between(sessionLastMessageAt, LocalDateTime.now(clock)) > staleDays;
    }

    /**
     * 生成会话摘要并持久化。
     * <p>从消息列表中分离出较早的消息用于生成摘要，保留最近的 N 条消息不被压缩。
     * 生成的摘要通过 upsert 写入 {@code assistant_session_context} 表。</p>
     *
     * @param sessionId          会话 ID
     * @param messages           会话全部消息列表
     * @param recentMessageLimit 保留最近消息的数量
     * @return 生成的摘要文本，若无需摘要则返回 {@code null}
     */
    public String summarizeAndPersist(Long sessionId, List<AssistantMessageEntity> messages, int recentMessageLimit) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        int keepRecentCount = Math.max(1, recentMessageLimit);
        int summaryMessageCount = Math.max(0, messages.size() - keepRecentCount);
        if (summaryMessageCount == 0) {
            return null;
        }
        List<AssistantMessageEntity> messagesForSummary = messages.subList(0, summaryMessageCount);
        String summaryText = buildSummaryText(messagesForSummary);
        AssistantSessionContextEntity entity = new AssistantSessionContextEntity();
        entity.setSessionId(sessionId);
        entity.setSummaryText(summaryText);
        entity.setSourceMessageId(messagesForSummary.getLast().getId());
        entity.setUpdatedAt(LocalDateTime.now(clock));
        int affectedRows = assistantSessionContextMapper.upsert(entity);
        if (affectedRows != 1) {
            throw new BusinessException("保存会话摘要失败");
        }
        return summaryText;
    }

    /**
     * 估算消息列表的 token 数。
     * <p>基于消息内容总字符数除以固定除数（{@value #TOKEN_ESTIMATE_DIVISOR}）进行粗略估算。</p>
     *
     * @param messages 消息实体列表
     * @return 估算的 token 数量，至少为 0
     */
    public int estimateTokens(List<AssistantMessageEntity> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int totalChars = messages.stream()
                .map(AssistantMessageEntity::getContent)
                .filter(content -> content != null && !content.isBlank())
                .mapToInt(String::length)
                .sum();
        return Math.max(1, totalChars / TOKEN_ESTIMATE_DIVISOR);
    }

    private boolean isStale(LocalDateTime summaryUpdatedAt, LocalDateTime sessionLastMessageAt) {
        if (summaryUpdatedAt == null) {
            return true;
        }
        if (sessionLastMessageAt != null && summaryUpdatedAt.isBefore(sessionLastMessageAt)) {
            return true;
        }
        return ChronoUnit.DAYS.between(summaryUpdatedAt, LocalDateTime.now(clock)) > staleDays;
    }

    private String buildSummaryText(List<AssistantMessageEntity> messages) {
        StringBuilder builder = new StringBuilder("历史摘要:").append(System.lineSeparator());
        int currentChars = builder.length();
        for (AssistantMessageEntity message : messages) {
            String line = "- %s：%s".formatted(roleLabel(message.getRole()), normalizeContent(message.getContent()));
            if (currentChars + line.length() > SUMMARY_SOURCE_CHAR_LIMIT) {
                builder.append("- 其余历史消息已省略").append(System.lineSeparator());
                break;
            }
            builder.append(line).append(System.lineSeparator());
            currentChars = builder.length();
        }
        return builder.toString().trim();
    }

    private String roleLabel(String role) {
        return AssistantMessageRole.USER.name().equals(role) ? "用户" : "助手";
    }

    private String normalizeContent(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 160) {
            return normalized;
        }
        return normalized.substring(0, 160) + "...";
    }
}

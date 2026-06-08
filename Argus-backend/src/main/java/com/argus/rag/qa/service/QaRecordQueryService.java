package com.argus.rag.qa.service;

import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.common.enums.SystemRole;
import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.qa.mapper.QaRecordCitationMapper;
import com.argus.rag.qa.mapper.QaRecordMapper;
import com.argus.rag.qa.model.entity.QaRecordCitationEntity;
import com.argus.rag.qa.model.entity.QaRecordEntity;
import com.argus.rag.qa.model.vo.QaRecordDetailVO;
import com.argus.rag.qa.model.vo.QaRecordListItemVO;
import com.argus.rag.qa.model.vo.QaRecordPageVO;
import com.argus.rag.qa.support.EvidenceOverviewAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** QA 问答历史查询服务，统一处理权限过滤、分页和响应组装。 */
@Service
public class QaRecordQueryService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int PREVIEW_LIMIT = 120;

    private final CurrentUserService currentUserService;
    private final QaRecordMapper qaRecordMapper;
    private final QaRecordCitationMapper qaRecordCitationMapper;
    private final EvidenceOverviewAssembler evidenceOverviewAssembler;

    public QaRecordQueryService(CurrentUserService currentUserService,
                                QaRecordMapper qaRecordMapper,
                                QaRecordCitationMapper qaRecordCitationMapper,
                                EvidenceOverviewAssembler evidenceOverviewAssembler) {
        this.currentUserService = currentUserService;
        this.qaRecordMapper = qaRecordMapper;
        this.qaRecordCitationMapper = qaRecordCitationMapper;
        this.evidenceOverviewAssembler = evidenceOverviewAssembler;
    }

    /** 分页查询当前用户可见的 QA 历史记录。 */
    public QaRecordPageVO list(Long groupId, Boolean answered, Integer page, Integer pageSize) {
        CurrentUserService.CurrentUser currentUser = currentUserService.getRequiredCurrentUser();
        boolean systemAdmin = currentUser.systemRole() == SystemRole.ADMIN;
        int safePage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int safePageSize = normalizePageSize(pageSize);
        long offset = (long) (safePage - 1) * safePageSize;

        List<QaRecordListItemVO> items = qaRecordMapper.selectVisibleRecords(
                currentUser.userId(),
                systemAdmin,
                groupId,
                answered,
                offset,
                safePageSize
        ).stream().map(this::toListItem).toList();
        Long total = qaRecordMapper.countVisibleRecords(
                currentUser.userId(),
                systemAdmin,
                groupId,
                answered);
        return new QaRecordPageVO(items, total == null ? 0L : total, safePage, safePageSize);
    }

    /** 查询单条 QA 记录详情，并附带回答时保存的引用快照。 */
    public QaRecordDetailVO getDetail(Long recordId) {
        if (recordId == null || recordId <= 0) {
            throw new BusinessException("QA record id is invalid");
        }
        CurrentUserService.CurrentUser currentUser = currentUserService.getRequiredCurrentUser();
        boolean systemAdmin = currentUser.systemRole() == SystemRole.ADMIN;
        QaRecordEntity record = qaRecordMapper.selectVisibleRecord(recordId, currentUser.userId(), systemAdmin);
        if (record == null) {
            throw new BusinessException("QA record does not exist or is not visible");
        }
        List<QaRecordDetailVO.Citation> citations = qaRecordCitationMapper.selectByRecordId(recordId)
                .stream()
                .map(this::toCitation)
                .toList();
        return toDetail(record, citations);
    }

    /** Deletes a visible QA history record and its persisted citation snapshots. */
    @Transactional
    public void delete(Long recordId) {
        if (recordId == null || recordId <= 0) {
            throw new BusinessException("QA record id is invalid");
        }
        CurrentUserService.CurrentUser currentUser = currentUserService.getRequiredCurrentUser();
        boolean systemAdmin = currentUser.systemRole() == SystemRole.ADMIN;
        QaRecordEntity record = qaRecordMapper.selectVisibleRecord(recordId, currentUser.userId(), systemAdmin);
        if (record == null) {
            throw new BusinessException("QA record does not exist or is not visible");
        }
        qaRecordCitationMapper.deleteByRecordId(recordId);
        qaRecordMapper.deleteById(recordId);
    }

    /** 规范分页大小，避免一次请求拉取过多历史记录。 */
    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /** 将数据库实体转换为列表页展示项。 */
    private QaRecordListItemVO toListItem(QaRecordEntity record) {
        return new QaRecordListItemVO(
                record.getId(),
                record.getUserId(),
                record.getGroupId(),
                record.getQuestion(),
                record.getAnswered(),
                preview(record),
                record.getReasonCode(),
                record.getEvidenceLevel(),
                record.getCitationCount(),
                record.getLatencyMs(),
                record.getCreatedAt()
        );
    }

    /** 将数据库实体和引用快照组合为详情响应。 */
    private QaRecordDetailVO toDetail(QaRecordEntity record, List<QaRecordDetailVO.Citation> citations) {
        return new QaRecordDetailVO(
                record.getId(),
                record.getUserId(),
                record.getGroupId(),
                record.getQuestion(),
                record.getAnswer(),
                record.getAnswered(),
                record.getReasonCode(),
                record.getReasonMessage(),
                record.getEvidenceLevel(),
                record.getCitationCount(),
                record.getPromptTokens(),
                record.getCompletionTokens(),
                record.getTotalTokens(),
                record.getIsEstimated(),
                record.getLatencyMs(),
                record.getModelName(),
                record.getEndpoint(),
                record.getSuccess(),
                record.getErrorMessage(),
                record.getCreatedAt(),
                evidenceOverviewAssembler.assembleHistoryCitations(citations),
                citations
        );
    }

    /** 将引用快照实体转换为详情页引用项。 */
    private QaRecordDetailVO.Citation toCitation(QaRecordCitationEntity entity) {
        return new QaRecordDetailVO.Citation(
                entity.getDocumentId(),
                entity.getDocumentVersionId(),
                entity.getChunkId(),
                entity.getChunkIndex(),
                entity.getStartChunkIndex(),
                entity.getEndChunkIndex(),
                entity.getFileName(),
                entity.getScore(),
                entity.getRetrievalSource(),
                entity.getVectorScore(),
                entity.getKeywordScore(),
                entity.getHybridScore(),
                entity.getSnippet()
        );
    }

    /** 生成列表页回答预览，优先取回答正文，其次取拒答原因。 */
    private String preview(QaRecordEntity record) {
        String source = StringUtils.hasText(record.getAnswer()) ? record.getAnswer() : record.getReasonMessage();
        if (!StringUtils.hasText(source)) {
            return "";
        }
        String normalized = source.replaceAll("\\s+", " ").trim();
        return normalized.length() <= PREVIEW_LIMIT ? normalized : normalized.substring(0, PREVIEW_LIMIT);
    }
}

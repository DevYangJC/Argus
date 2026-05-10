package com.argus.rag.document.service;

import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.common.enums.DocumentStatus;
import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.document.mapper.DocumentMapper;
import com.argus.rag.document.model.dto.DocumentQuery;
import com.argus.rag.document.model.vo.DocumentListItemVO;
import com.argus.rag.group.service.GroupMembershipService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 文档查询服务。
 */
@Service
public class DocumentQueryService {

    private final DocumentMapper documentMapper;
    private final GroupMembershipService groupMembershipService;
    private final CurrentUserService currentUserService;

    public DocumentQueryService(DocumentMapper documentMapper,
                                GroupMembershipService groupMembershipService,
                                CurrentUserService currentUserService) {
        this.documentMapper = documentMapper;
        this.groupMembershipService = groupMembershipService;
        this.currentUserService = currentUserService;
    }

    /** 查询文档列表，按查询条件筛选当前用户有权查看的文档 */
    public List<DocumentListItemVO> listDocuments(DocumentQuery query) {
        DocumentQuery validatedQuery = normalizeQuery(query);
        return documentMapper.selectReadableDocuments(validatedQuery);
    }

    private DocumentQuery normalizeQuery(DocumentQuery query) {
        DocumentQuery safeQuery = query == null ? new DocumentQuery() : query;
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser();
        safeQuery.setCurrentUserId(currentUser.userId());
        if (safeQuery.getGroupId() != null) {
            requireGroupId(safeQuery.getGroupId());
            groupMembershipService.requireGroupReadable(safeQuery.getGroupId());
        }
        if (safeQuery.getUploaderUserId() != null && safeQuery.getUploaderUserId() <= 0) {
            throw new BusinessException("uploaderUserId 非法");
        }
        if (safeQuery.getUploadedFrom() != null
                && safeQuery.getUploadedTo() != null
                && safeQuery.getUploadedFrom().isAfter(safeQuery.getUploadedTo())) {
            throw new BusinessException("uploadedFrom 不能晚于 uploadedTo");
        }
        if (StringUtils.hasText(safeQuery.getGroupRelation())) {
            safeQuery.setGroupRelation(normalizeGroupRelation(safeQuery.getGroupRelation()));
        }
        if (StringUtils.hasText(safeQuery.getStatus())) {
            safeQuery.setStatus(normalizeStatus(safeQuery.getStatus()));
        }
        if (StringUtils.hasText(safeQuery.getFileName())) {
            safeQuery.setFileName(safeQuery.getFileName().trim());
        }
        return safeQuery;
    }

    private String normalizeGroupRelation(String groupRelation) {
        String normalized = groupRelation.trim().toUpperCase();
        return switch (normalized) {
            case "OWNER", "OWNED" -> "OWNED";
            case "MEMBER", "JOINED" -> "JOINED";
            default -> throw new BusinessException("groupRelation 非法");
        };
    }

    private String normalizeStatus(String status) {
        try {
            return DocumentStatus.valueOf(status.trim().toUpperCase()).name();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("status 非法");
        }
    }

    private void requireGroupId(Long groupId) {
        if (groupId == null || groupId <= 0) {
            throw new BusinessException("groupId 非法");
        }
    }
}

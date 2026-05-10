package com.argus.rag.document.service;

import com.argus.rag.common.enums.DocumentStatus;
import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.document.mapper.DocumentMapper;
import com.argus.rag.document.model.entity.DocumentEntity;
import com.argus.rag.document.model.vo.DocumentPreviewVO;
import com.argus.rag.group.service.GroupMembershipService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 文档预览服务。
 */
@Service
public class DocumentPreviewService {

    private static final int PREVIEW_MAX_LENGTH = 200;

    private final DocumentMapper documentMapper;
    private final GroupMembershipService groupMembershipService;

    public DocumentPreviewService(DocumentMapper documentMapper,
                                  GroupMembershipService groupMembershipService) {
        this.documentMapper = documentMapper;
        this.groupMembershipService = groupMembershipService;
    }

    /** 预览文档文本内容（前 200 字符），需群组成员权限 */
    public DocumentPreviewVO previewDocument(Long userId, Long groupId, Long documentId) {
        requireGroupId(groupId);
        groupMembershipService.requireGroupReadable(groupId);
        if (documentId == null || documentId <= 0) {
            throw new BusinessException("文档ID非法");
        }
        DocumentEntity document = documentMapper.selectByIdAndGroupId(documentId, groupId);
        if (document == null) {
            throw new BusinessException("文档不存在或已删除");
        }
        if (!DocumentStatus.READY.name().equals(document.getStatus())) {
            throw new BusinessException("文档尚未就绪，暂不可预览");
        }
        if (!StringUtils.hasText(document.getPreviewText())) {
            throw new BusinessException("文档暂无可预览内容");
        }
        DocumentPreviewVO preview = new DocumentPreviewVO();
        preview.setDocumentId(document.getId());
        preview.setFileName(document.getFileName());
        preview.setPreviewText(trimPreviewText(document.getPreviewText()));
        return preview;
    }

    private String trimPreviewText(String previewText) {
        if (!StringUtils.hasText(previewText) || previewText.length() <= PREVIEW_MAX_LENGTH) {
            return previewText;
        }
        return previewText.substring(0, PREVIEW_MAX_LENGTH);
    }

    private void requireGroupId(Long groupId) {
        if (groupId == null || groupId <= 0) {
            throw new BusinessException("groupId 非法");
        }
    }
}

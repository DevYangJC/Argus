package com.argus.rag.group.service;

import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.common.enums.GroupJoinRequestStatus;
import com.argus.rag.common.enums.GroupRole;
import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.group.mapper.GroupJoinRequestMapper;
import com.argus.rag.group.model.dto.CreateJoinRequestRequest;
import com.argus.rag.group.model.entity.Group;
import com.argus.rag.group.model.entity.GroupJoinRequest;
import com.argus.rag.group.model.vo.MyJoinRequestResponse;
import com.argus.rag.group.model.vo.OwnerJoinRequestResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 群组加入申请服务，提供申请提交、审批、查询等功能。
 */
@Slf4j
@Service
public class GroupJoinRequestService {

    private final GroupJoinRequestMapper groupJoinRequestMapper;
    private final GroupMembershipService groupMembershipService;
    private final CurrentUserService currentUserService;

    public GroupJoinRequestService(
            GroupJoinRequestMapper groupJoinRequestMapper,
            GroupMembershipService groupMembershipService,
            CurrentUserService currentUserService
    ) {
        this.groupJoinRequestMapper = groupJoinRequestMapper;
        this.groupMembershipService = groupMembershipService;
        this.currentUserService = currentUserService;
    }

    /** 提交加入群组申请 */
    @Transactional
    public Long submitJoinRequest(CreateJoinRequestRequest joinRequestRequest) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser();
        String groupCode = requireGroupCode(joinRequestRequest.groupCode());
        GroupSummary group = loadGroupByCode(groupCode);
        rejectExistingMembership(group.groupId(), currentUser.userId(), "该用户已经是知识库成员");
        if (hasRows(groupJoinRequestMapper.countPendingInvitation(group.groupId(), currentUser.userId()))) {
            throw new BusinessException("该知识库已有待处理邀请，请先处理邀请");
        }
        if (hasRows(groupJoinRequestMapper.countPendingJoinRequest(group.groupId(), currentUser.userId()))) {
            throw new BusinessException("该用户已有待处理加入申请，请先等待审批");
        }
        Long requestId = groupJoinRequestMapper.insertPendingJoinRequestReturningId(
                group.groupId(),
                currentUser.userId(),
                GroupJoinRequestStatus.PENDING.name()
        );
        log.info("提交加入申请: userId={}, groupId={}, requestId={}", currentUser.userId(), group.groupId(), requestId);
        return requestId;
    }

    /** 查询当前用户的所有加入申请 */
    public List<MyJoinRequestResponse> listMyJoinRequests() {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser();
        return groupJoinRequestMapper.selectMyJoinRequests(currentUser.userId());
    }

    /** 查询指定群组的待处理加入申请（仅 OWNER 可查） */
    public List<OwnerJoinRequestResponse> listOwnerJoinRequests(Long groupId) {
        Long requiredGroupId = requirePositiveId(groupId, "groupId 非法");
        groupMembershipService.requireGroupOwner(requiredGroupId);
        return groupJoinRequestMapper.selectPendingJoinRequestsByGroupId(requiredGroupId);
    }

    /** 审批通过加入申请 */
    @Transactional
    public void approveJoinRequest(Long groupId, Long requestId) {
        CurrentUserService.CurrentUser owner = requireOwnerAndLoadUser(groupId);
        JoinRequest joinRequest = loadJoinRequest(requestId);
        requireSameGroup(groupId, joinRequest);
        requirePending(joinRequest);
        rejectExistingMembership(joinRequest.groupId(), joinRequest.applicantUserId(), "该用户已经是知识库成员");
        groupJoinRequestMapper.insertMembership(
                joinRequest.groupId(),
                joinRequest.applicantUserId(),
                GroupRole.MEMBER.name()
        );
        updateStatus(joinRequest.requestId(), GroupJoinRequestStatus.APPROVED, owner.userId());
        log.info("审批通过加入申请: requestId={}, groupId={}, applicantUserId={}, decidedByUserId={}",
                joinRequest.requestId(), joinRequest.groupId(), joinRequest.applicantUserId(), owner.userId());
    }

    /** 拒绝加入申请 */
    @Transactional
    public void rejectJoinRequest(Long groupId, Long requestId) {
        CurrentUserService.CurrentUser owner = requireOwnerAndLoadUser(groupId);
        JoinRequest joinRequest = loadJoinRequest(requestId);
        requireSameGroup(groupId, joinRequest);
        requirePending(joinRequest);
        updateStatus(joinRequest.requestId(), GroupJoinRequestStatus.REJECTED, owner.userId());
        log.info("拒绝加入申请: requestId={}, groupId={}, applicantUserId={}, decidedByUserId={}",
                joinRequest.requestId(), joinRequest.groupId(), joinRequest.applicantUserId(), owner.userId());
    }

    private CurrentUserService.CurrentUser requireOwnerAndLoadUser(Long groupId) {
        Long requiredGroupId = requirePositiveId(groupId, "groupId 非法");
        return groupMembershipService.requireGroupOwner(requiredGroupId);
    }

    private String requireGroupCode(String groupCode) {
        if (!StringUtils.hasText(groupCode)) {
            throw new BusinessException("组织 ID 不能为空");
        }
        return groupCode.trim();
    }

    private GroupSummary loadGroupByCode(String groupCode) {
        Group group = groupJoinRequestMapper.selectActiveGroupByCode(groupCode);
        if (group == null) {
            throw new BusinessException("组织 ID 不存在");
        }
        return new GroupSummary(group.getId());
    }

    private JoinRequest loadJoinRequest(Long requestId) {
        Long requiredRequestId = requirePositiveId(requestId, "申请ID非法");
        GroupJoinRequest entity = groupJoinRequestMapper.selectJoinRequestById(requiredRequestId);
        if (entity == null) {
            throw new BusinessException("申请不存在");
        }
        return new JoinRequest(
                entity.getId(),
                entity.getGroupId(),
                entity.getApplicantUserId(),
                entity.getStatus().name()
        );
    }

    private void rejectExistingMembership(Long groupId, Long userId, String message) {
        if (hasRows(groupJoinRequestMapper.countActiveMembership(groupId, userId))) {
            throw new BusinessException(message);
        }
    }

    private void requireSameGroup(Long groupId, JoinRequest joinRequest) {
        Long requiredGroupId = requirePositiveId(groupId, "groupId 非法");
        if (!requiredGroupId.equals(joinRequest.groupId())) {
            throw new BusinessException("申请不存在");
        }
    }

    private void requirePending(JoinRequest joinRequest) {
        if (!GroupJoinRequestStatus.PENDING.name().equals(joinRequest.status())) {
            throw new BusinessException("申请已处理");
        }
    }

    private void updateStatus(Long requestId, GroupJoinRequestStatus status, Long decidedByUserId) {
        int updated = groupJoinRequestMapper.updateJoinRequestStatus(
                requestId,
                GroupJoinRequestStatus.PENDING.name(),
                status.name(),
                decidedByUserId
        );
        if (updated == 0) {
            throw new BusinessException("申请已处理");
        }
    }

    private Long requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new BusinessException(message);
        }
        return id;
    }

    private boolean hasRows(Long count) {
        return count != null && count > 0;
    }

    /** 群组摘要信息（内部使用） */
    private record GroupSummary(
            /** 群组 ID */
            Long groupId
    ) {
    }

    /** 加入申请信息（内部使用） */
    private record JoinRequest(
            /** 申请 ID */
            Long requestId,
            /** 群组 ID */
            Long groupId,
            /** 申请人用户 ID */
            Long applicantUserId,
            /** 申请状态 */
            String status
    ) {
    }
}

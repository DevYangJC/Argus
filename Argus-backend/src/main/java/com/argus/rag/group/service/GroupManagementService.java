package com.argus.rag.group.service;

import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.common.enums.GroupInvitationStatus;
import com.argus.rag.common.enums.GroupRole;
import com.argus.rag.common.enums.GroupStatus;
import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.group.mapper.GroupJoinRequestMapper;
import com.argus.rag.group.mapper.GroupMembershipMapper;
import com.argus.rag.group.model.entity.GroupInvitation;
import com.argus.rag.group.model.dto.CreateGroupRequest;
import com.argus.rag.group.model.dto.CreateInvitationRequest;
import com.argus.rag.group.model.vo.GroupMemberResponse;
import com.argus.rag.group.model.vo.MySentInvitationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * 群组管理服务，提供群组创建、成员管理、邀请管理等功能。
 */
@Slf4j
@Service
public class GroupManagementService {

    private static final int MAX_GROUP_NAME_LENGTH = 128;
    private static final int MAX_GROUP_DESCRIPTION_LENGTH = 512;
    private final GroupMembershipMapper groupMembershipMapper;
    private final GroupJoinRequestMapper groupJoinRequestMapper;
    private final GroupMembershipService groupMembershipService;
    private final CurrentUserService currentUserService;

    public GroupManagementService(
            GroupMembershipMapper groupMembershipMapper,
            GroupJoinRequestMapper groupJoinRequestMapper,
            GroupMembershipService groupMembershipService,
            CurrentUserService currentUserService
    ) {
        this.groupMembershipMapper = groupMembershipMapper;
        this.groupJoinRequestMapper = groupJoinRequestMapper;
        this.groupMembershipService = groupMembershipService;
        this.currentUserService = currentUserService;
    }

    /** 创建新群组，创建者自动成为 OWNER */
    @Transactional
    public Long createGroup(CreateGroupRequest createGroupRequest) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser();
        String groupName = requireGroupName(createGroupRequest.name());
        String description = normalizeDescription(createGroupRequest.description());
        Long groupId = groupMembershipMapper.insertGroupReturningId(
                buildGroupCode(),
                groupName,
                description,
                currentUser.userId(),
                GroupStatus.ACTIVE.name()
        );
        groupMembershipMapper.insertMembership(groupId, currentUser.userId(), GroupRole.OWNER.name());
        log.info("创建群组成功: groupId={}, groupName={}, ownerUserId={}", groupId, groupName, currentUser.userId());
        return groupId;
    }

    /** 创建群组邀请（仅 OWNER 可操作） */
    @Transactional
    public Long createInvitation(Long groupId, CreateInvitationRequest createInvitationRequest) {
        Long requiredGroupId = requirePositiveId(groupId, "groupId 非法");
        Long inviteeUserId = requirePositiveId(createInvitationRequest.inviteeUserId(), "被邀请用户非法");
        CurrentUserService.CurrentUser currentUser = groupMembershipService.requireGroupOwner(requiredGroupId);
        rejectMissingUser(inviteeUserId);
        rejectDuplicateInvitationTarget(requiredGroupId, inviteeUserId);
        Long invitationId = groupMembershipMapper.insertPendingInvitationReturningId(
                requiredGroupId,
                currentUser.userId(),
                inviteeUserId,
                GroupInvitationStatus.PENDING.name()
        );
        log.info("创建群组邀请: groupId={}, inviterUserId={}, inviteeUserId={}, invitationId={}",
                requiredGroupId, currentUser.userId(), inviteeUserId, invitationId);
        return invitationId;
    }

    /** 接受群组邀请 */
    @Transactional
    public void acceptInvitation(Long invitationId) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser();
        Invitation invitation = loadInvitation(invitationId);
        requireInvitee(currentUser, invitation);
        requirePending(invitation);
        rejectExistingMembership(invitation.groupId(), invitation.inviteeUserId());
        groupMembershipMapper.insertMembership(
                invitation.groupId(),
                invitation.inviteeUserId(),
                GroupRole.MEMBER.name()
        );
        updateInvitationStatus(invitation.id(), GroupInvitationStatus.ACCEPTED);
        log.info("接受群组邀请: invitationId={}, groupId={}, userId={}", invitation.id(), invitation.groupId(), invitation.inviteeUserId());
    }

    /** 拒绝群组邀请 */
    @Transactional
    public void rejectInvitation(Long invitationId) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser();
        Invitation invitation = loadInvitation(invitationId);
        requireInvitee(currentUser, invitation);
        requirePending(invitation);
        updateInvitationStatus(invitation.id(), GroupInvitationStatus.REJECTED);
        log.info("拒绝群组邀请: invitationId={}, groupId={}, userId={}", invitation.id(), invitation.groupId(), invitation.inviteeUserId());
    }

    /** 取消群组邀请（仅群组 OWNER 可操作） */
    @Transactional
    public void cancelInvitation(Long invitationId) {
        Invitation invitation = loadInvitation(invitationId);
        groupMembershipService.requireGroupOwner(invitation.groupId());
        requirePending(invitation);
        updateInvitationStatus(invitation.id(), GroupInvitationStatus.CANCELED);
        log.info("取消群组邀请: invitationId={}, groupId={}", invitation.id(), invitation.groupId());
    }

    /** 查询当前用户发出的所有邀请 */
    public List<MySentInvitationResponse> listMySentInvitations() {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser();
        return groupMembershipMapper.selectSentInvitationsByInviterUserId(currentUser.userId());
    }

    /** 查询群组成员列表（仅 OWNER 可查） */
    public List<GroupMemberResponse> listMembers(Long groupId) {
        Long requiredGroupId = requirePositiveId(groupId, "groupId 非法");
        groupMembershipService.requireGroupOwner(requiredGroupId);
        return groupMembershipMapper.selectMembersByGroupId(requiredGroupId);
    }

    /** 移除群组成员（仅 OWNER 可操作，不能移除 OWNER） */
    @Transactional
    public void removeMember(Long groupId, Long userId) {
        Long requiredGroupId = requirePositiveId(groupId, "groupId 非法");
        Long requiredUserId = requirePositiveId(userId, "成员用户非法");
        groupMembershipService.requireGroupOwner(requiredGroupId);
        String role = groupMembershipMapper.selectActiveMembershipRole(requiredUserId, requiredGroupId);
        if (role == null) {
            throw new BusinessException("成员不存在");
        }
        if (GroupRole.OWNER.name().equals(role)) {
            throw new BusinessException("不能移除 OWNER");
        }
        groupMembershipMapper.deleteMembership(requiredGroupId, requiredUserId);
        log.info("移除群组成员: groupId={}, userId={}", requiredGroupId, requiredUserId);
    }

    /** 退出群组（OWNER 不能退出自己的组） */
    @Transactional
    public void leaveGroup(Long groupId) {
        Long requiredGroupId = requirePositiveId(groupId, "groupId 非法");
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser();
        String role = groupMembershipMapper.selectActiveMembershipRole(currentUser.userId(), requiredGroupId);
        if (role == null) {
            throw new BusinessException("当前用户不是目标群组成员");
        }
        if (GroupRole.OWNER.name().equals(role)) {
            throw new BusinessException("OWNER 不能退出自己的组");
        }
        groupMembershipMapper.deleteMembership(requiredGroupId, currentUser.userId());
        log.info("退出群组: groupId={}, userId={}", requiredGroupId, currentUser.userId());
    }

    private void rejectDuplicateInvitationTarget(Long groupId, Long inviteeUserId) {
        rejectExistingMembership(groupId, inviteeUserId);
        if (hasRows(groupMembershipMapper.countPendingInvitation(groupId, inviteeUserId))) {
            throw new BusinessException("已存在待处理邀请");
        }
        if (hasRows(groupJoinRequestMapper.countPendingJoinRequest(groupId, inviteeUserId))) {
            throw new BusinessException("该用户已有待处理加入申请，请先审批申请");
        }
    }

    private void rejectExistingMembership(Long groupId, Long inviteeUserId) {
        if (hasRows(groupMembershipMapper.countMembershipByGroupIdAndUserId(groupId, inviteeUserId))) {
            throw new BusinessException("被邀请人已是群组成员");
        }
    }

    private void rejectMissingUser(Long userId) {
        if (!hasRows(groupMembershipMapper.countUserById(userId))) {
            throw new BusinessException("被邀请用户不存在");
        }
    }

    private Invitation loadInvitation(Long invitationId) {
        Long requiredInvitationId = requirePositiveId(invitationId, "邀请ID非法");
        GroupInvitation entity = groupMembershipMapper.selectInvitationById(requiredInvitationId);
        if (entity == null) {
            throw new BusinessException("邀请不存在");
        }
        return new Invitation(
                entity.getId(),
                entity.getGroupId(),
                entity.getInviterUserId(),
                entity.getInviteeUserId(),
                entity.getStatus().name()
        );
    }

    private void requireInvitee(CurrentUserService.CurrentUser currentUser, Invitation invitation) {
        if (!currentUser.userId().equals(invitation.inviteeUserId())) {
            throw new BusinessException("无权处理该邀请");
        }
    }

    private void requirePending(Invitation invitation) {
        if (!GroupInvitationStatus.PENDING.name().equals(invitation.status())) {
            throw new BusinessException("邀请已处理");
        }
    }

    private void updateInvitationStatus(Long invitationId, GroupInvitationStatus status) {
        int updated = groupMembershipMapper.updateInvitationStatus(
                invitationId,
                GroupInvitationStatus.PENDING.name(),
                status.name()
        );
        if (updated == 0) {
            throw new BusinessException("邀请已处理");
        }
    }

    private String requireGroupName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("组名称不能为空");
        }
        String trimmedName = name.trim();
        if (trimmedName.length() > MAX_GROUP_NAME_LENGTH) {
            throw new BusinessException("组名称不能超过 128");
        }
        return trimmedName;
    }

    private String normalizeDescription(String description) {
        if (!StringUtils.hasText(description)) {
            return "";
        }
        String trimmedDescription = description.trim();
        if (trimmedDescription.length() > MAX_GROUP_DESCRIPTION_LENGTH) {
            throw new BusinessException("组描述不能超过 512");
        }
        return trimmedDescription;
    }

    private Long requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new BusinessException(message);
        }
        return id;
    }

    private String buildGroupCode() {
        return "group-" + UUID.randomUUID().toString().replace("-", "");
    }

    private boolean hasRows(Long count) {
        return count != null && count > 0;
    }

    /** 邀请信息（内部使用） */
    private record Invitation(
            /** 邀请 ID */
            Long id,
            /** 群组 ID */
            Long groupId,
            /** 邀请人用户 ID */
            Long inviterUserId,
            /** 被邀请人用户 ID */
            Long inviteeUserId,
            /** 邀请状态 */
            String status
    ) {
    }
}

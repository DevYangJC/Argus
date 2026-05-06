package com.argus.rag.groupmembership.service;

import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.common.enums.GroupInvitationStatus;
import com.argus.rag.common.enums.GroupRole;
import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.groupmembership.mapper.GroupMembershipMapper;
import com.argus.rag.groupmembership.model.entity.Group;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 群组成员关系服务，提供群组可见性查询、权限校验等功能。
 */
@Slf4j
@Service
public class GroupMembershipService {

    private static final String NON_MEMBER_MESSAGE = "当前用户不是目标群组成员";
    private static final String NON_OWNER_MESSAGE = "当前用户不是目标群组 OWNER";
    private static final String EXISTING_MEMBER_MESSAGE = "被邀请人已是群组成员";
    private static final String EXISTING_PENDING_INVITATION_MESSAGE = "已存在待处理邀请";
    private final GroupMembershipMapper groupMembershipMapper;
    private final CurrentUserService currentUserService;

    public GroupMembershipService(
            GroupMembershipMapper groupMembershipMapper,
            CurrentUserService currentUserService
    ) {
        this.groupMembershipMapper = groupMembershipMapper;
        this.currentUserService = currentUserService;
    }

    /** 查询当前用户可见的群组列表（拥有的、加入的、待处理的邀请） */
    public GroupQueryResult listVisibleGroups() {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser();
        return new GroupQueryResult(
                toVisibleGroups(groupMembershipMapper.selectOwnedGroupsByUserId(currentUser.userId())),
                toVisibleGroups(groupMembershipMapper.selectJoinedGroupsByUserId(currentUser.userId())),
                toPendingInvitations(groupMembershipMapper.selectPendingInvitationsByInviteeUserId(currentUser.userId()))
        );
    }

    /** 要求当前用户为目标群组成员，否则抛出异常，返回当前用户 */
    public CurrentUserService.CurrentUser requireCurrentUserMember(Long groupId) {
        return requireGroupReadable(groupId);
    }

    /** 要求当前用户可读目标群组（即为其成员），否则抛出异常，返回当前用户 */
    public CurrentUserService.CurrentUser requireGroupReadable(Long groupId) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser();
        String role = groupMembershipMapper.selectActiveMembershipRole(currentUser.userId(), requireGroupId(groupId));
        if (role == null) {
            throw new BusinessException(NON_MEMBER_MESSAGE);
        }
        return currentUser;
    }

    /** 要求当前用户为目标群组 OWNER，否则抛出异常，返回当前用户 */
    public CurrentUserService.CurrentUser requireGroupOwner(Long groupId) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser();
        String role = groupMembershipMapper.selectActiveMembershipRole(currentUser.userId(), requireGroupId(groupId));
        if (role == null) {
            throw new BusinessException(NON_MEMBER_MESSAGE);
        }
        if (!GroupRole.OWNER.name().equals(role)) {
            throw new BusinessException(NON_OWNER_MESSAGE);
        }
        return currentUser;
    }

    /** 创建待处理的邀请（由其他服务内部调用，调用方需确保已通过权限校验） */
    @Transactional
    public void createPendingInvitation(Long groupId, Long inviteeUserId) {
        Long requiredGroupId = requireGroupId(groupId);
        Long requiredInviteeUserId = requireUserId(inviteeUserId);
        CurrentUserService.CurrentUser currentUser = requireGroupOwner(requiredGroupId);
        rejectDuplicateInvitationTarget(requiredGroupId, requiredInviteeUserId);
        groupMembershipMapper.insertPendingInvitation(
                requiredGroupId,
                currentUser.userId(),
                requiredInviteeUserId,
                GroupInvitationStatus.PENDING.name()
        );
        log.info("创建待处理邀请: groupId={}, inviterUserId={}, inviteeUserId={}", requiredGroupId, currentUser.userId(), requiredInviteeUserId);
    }

    private void rejectDuplicateInvitationTarget(Long groupId, Long inviteeUserId) {
        if (hasRows(groupMembershipMapper.countMembershipByGroupIdAndUserId(groupId, inviteeUserId))) {
            throw new BusinessException(EXISTING_MEMBER_MESSAGE);
        }
        if (hasRows(groupMembershipMapper.countPendingInvitation(groupId, inviteeUserId))) {
            throw new BusinessException(EXISTING_PENDING_INVITATION_MESSAGE);
        }
    }

    private Long requireGroupId(Long groupId) {
        if (groupId == null || groupId <= 0) {
            throw new BusinessException("groupId 非法");
        }
        return groupId;
    }

    private Long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("被邀请用户非法");
        }
        return userId;
    }

    private boolean hasRows(Long count) {
        return count != null && count > 0;
    }

    private List<VisibleGroup> toVisibleGroups(List<Map<String, Object>> rows) {
        return rows.stream().map(this::toVisibleGroup).toList();
    }

    private List<PendingInvitation> toPendingInvitations(List<Map<String, Object>> rows) {
        return rows.stream().map(this::toPendingInvitation).toList();
    }


    private VisibleGroup toVisibleGroup(Map<String, Object> row) {
        Number groupId = (Number) row.get("groupId");
        return new VisibleGroup(groupId.longValue(), String.valueOf(row.get("groupCode")), String.valueOf(row.get("groupName")));
    }

    private PendingInvitation toPendingInvitation(Map<String, Object> row) {
        return new PendingInvitation(
                toLong(row.get("invitationId")),
                toLong(row.get("groupId")),
                String.valueOf(row.get("groupName")),
                toLong(row.get("inviterUserId")),
                String.valueOf(row.get("inviterDisplayName")),
                String.valueOf(row.get("status"))
        );
    }

    private Long toLong(Object value) {
        return ((Number) value).longValue();
    }

    /** 群组查询结果，包含拥有的群组、加入的群组和待处理邀请 */
    public record GroupQueryResult(
            /** 拥有的群组列表 */
            List<VisibleGroup> ownedGroups,
            /** 加入的群组列表（不含已拥有的） */
            List<VisibleGroup> joinedGroups,
            /** 待处理的邀请列表 */
            List<PendingInvitation> pendingInvitations
    ) {
    }

    /** 可见群组摘要信息 */
    public record VisibleGroup(
            /** 群组 ID */
            Long groupId,
            /** 群组编码 */
            String groupCode,
            /** 群组名称 */
            String groupName
    ) {
    }

    /** 待处理邀请信息 */
    public record PendingInvitation(
            /** 邀请 ID */
            Long invitationId,
            /** 群组 ID */
            Long groupId,
            /** 群组名称 */
            String groupName,
            /** 邀请人用户 ID */
            Long inviterUserId,
            /** 邀请人显示名称 */
            String inviterDisplayName,
            /** 邀请状态 */
            String status
    ) {
    }
}

package com.argus.rag.group.controller;

import com.argus.rag.common.api.ApiResponse;
import com.argus.rag.common.log.OperationLog;
import com.argus.rag.group.model.dto.CreateGroupRequest;
import com.argus.rag.group.model.dto.CreateInvitationRequest;
import com.argus.rag.group.model.vo.GroupMemberResponse;
import com.argus.rag.group.model.vo.MySentInvitationResponse;
import com.argus.rag.group.service.GroupManagementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 群组管理控制器，提供群组创建、成员管理、邀请管理等接口。
 */
@OperationLog
@RestController
@RequestMapping("/api/groups")
public class GroupManagementController {

    private final GroupManagementService groupManagementService;

    public GroupManagementController(GroupManagementService groupManagementService) {
        this.groupManagementService = groupManagementService;
    }

    /** 创建新群组 */
    @PostMapping
    public ApiResponse<Long> createGroup(@Valid @RequestBody CreateGroupRequest createGroupRequest) {
        return ApiResponse.success(groupManagementService.createGroup(createGroupRequest));
    }

    /** 创建群组邀请 */
    @PostMapping("/{groupId}/invitations")
    public ApiResponse<Long> createInvitation(
            @PathVariable Long groupId,
            @Valid @RequestBody CreateInvitationRequest createInvitationRequest
    ) {
        return ApiResponse.success(groupManagementService.createInvitation(groupId, createInvitationRequest));
    }

    /** 查询当前用户发出的所有邀请 */
    @GetMapping("/invitations/my-sent")
    public ApiResponse<List<MySentInvitationResponse>> listMySentInvitations() {
        return ApiResponse.success(groupManagementService.listMySentInvitations());
    }

    /** 查询群组成员列表 */
    @GetMapping("/{groupId}/members")
    public ApiResponse<List<GroupMemberResponse>> listMembers(@PathVariable Long groupId) {
        return ApiResponse.success(groupManagementService.listMembers(groupId));
    }

    /** 移除群组成员 */
    @DeleteMapping("/{groupId}/members/{userId}")
    public ApiResponse<Void> removeMember(
            @PathVariable Long groupId,
            @PathVariable Long userId
    ) {
        groupManagementService.removeMember(groupId, userId);
        return ApiResponse.success(null);
    }

    /** 退出群组 */
    @PostMapping("/{groupId}/leave")
    public ApiResponse<Void> leaveGroup(@PathVariable Long groupId) {
        groupManagementService.leaveGroup(groupId);
        return ApiResponse.success(null);
    }
}

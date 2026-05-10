package com.argus.rag.group.controller;

import com.argus.rag.common.api.ApiResponse;
import com.argus.rag.common.log.OperationLog;
import com.argus.rag.group.service.GroupManagementService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 邀请决策控制器，提供接受、拒绝、取消邀请等接口。
 */
@OperationLog
@RestController
@RequestMapping("/api/invitations")
public class InvitationDecisionController {

    private final GroupManagementService groupManagementService;

    public InvitationDecisionController(GroupManagementService groupManagementService) {
        this.groupManagementService = groupManagementService;
    }

    /** 接受群组邀请 */
    @PostMapping("/{invitationId}/accept")
    public ApiResponse<Void> acceptInvitation(@PathVariable Long invitationId) {
        groupManagementService.acceptInvitation(invitationId);
        return ApiResponse.success(null);
    }

    /** 拒绝群组邀请 */
    @PostMapping("/{invitationId}/reject")
    public ApiResponse<Void> rejectInvitation(@PathVariable Long invitationId) {
        groupManagementService.rejectInvitation(invitationId);
        return ApiResponse.success(null);
    }

    /** 取消群组邀请 */
    @PostMapping("/{invitationId}/cancel")
    public ApiResponse<Void> cancelInvitation(@PathVariable Long invitationId) {
        groupManagementService.cancelInvitation(invitationId);
        return ApiResponse.success(null);
    }
}

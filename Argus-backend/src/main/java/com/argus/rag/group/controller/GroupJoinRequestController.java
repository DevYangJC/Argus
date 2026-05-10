package com.argus.rag.group.controller;

import com.argus.rag.common.api.ApiResponse;
import com.argus.rag.common.log.OperationLog;
import com.argus.rag.group.model.dto.CreateJoinRequestRequest;
import com.argus.rag.group.model.vo.MyJoinRequestResponse;
import com.argus.rag.group.model.vo.OwnerJoinRequestResponse;
import com.argus.rag.group.service.GroupJoinRequestService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 群组加入申请控制器，提供申请提交、审批、查询等接口。
 */
@OperationLog
@RestController
@RequestMapping("/api/groups")
public class GroupJoinRequestController {

    private final GroupJoinRequestService groupJoinRequestService;

    public GroupJoinRequestController(GroupJoinRequestService groupJoinRequestService) {
        this.groupJoinRequestService = groupJoinRequestService;
    }

    /** 提交加入群组申请 */
    @PostMapping("/join-requests")
    public ApiResponse<Long> submitJoinRequest(@Valid @RequestBody CreateJoinRequestRequest joinRequestRequest) {
        return ApiResponse.success(groupJoinRequestService.submitJoinRequest(joinRequestRequest));
    }

    /** 查询当前用户的所有加入申请 */
    @GetMapping("/join-requests/my")
    public ApiResponse<List<MyJoinRequestResponse>> listMyJoinRequests() {
        return ApiResponse.success(groupJoinRequestService.listMyJoinRequests());
    }

    /** 查询指定群组的待处理加入申请（仅 OWNER 可查） */
    @GetMapping("/{groupId}/join-requests")
    public ApiResponse<List<OwnerJoinRequestResponse>> listOwnerJoinRequests(@PathVariable Long groupId) {
        return ApiResponse.success(groupJoinRequestService.listOwnerJoinRequests(groupId));
    }

    /** 审批通过加入申请 */
    @PostMapping("/{groupId}/join-requests/{requestId}/approve")
    public ApiResponse<Void> approveJoinRequest(
            @PathVariable Long groupId,
            @PathVariable Long requestId
    ) {
        groupJoinRequestService.approveJoinRequest(groupId, requestId);
        return ApiResponse.success(null);
    }

    /** 拒绝加入申请 */
    @PostMapping("/{groupId}/join-requests/{requestId}/reject")
    public ApiResponse<Void> rejectJoinRequest(
            @PathVariable Long groupId,
            @PathVariable Long requestId
    ) {
        groupJoinRequestService.rejectJoinRequest(groupId, requestId);
        return ApiResponse.success(null);
    }
}

package com.argus.rag.group.controller;

import com.argus.rag.common.api.ApiResponse;
import com.argus.rag.common.log.OperationLog;
import com.argus.rag.group.service.GroupMembershipService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 群组查询控制器，提供当前用户可见群组查询接口。
 */
@OperationLog
@RestController
@RequestMapping("/api/groups")
public class GroupQueryController {

    private final GroupMembershipService groupMembershipService;

    public GroupQueryController(GroupMembershipService groupMembershipService) {
        this.groupMembershipService = groupMembershipService;
    }

    /** 获取当前用户可见的群组列表（拥有的、加入的、待处理邀请） */
    @GetMapping("/my")
    public ApiResponse<GroupMembershipService.GroupQueryResult> listVisibleGroups() {
        return ApiResponse.success(groupMembershipService.listVisibleGroups());
    }
}

package com.argus.rag.user.controller;

import com.argus.rag.common.api.ApiResponse;
import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.common.log.OperationLog;
import com.argus.rag.user.model.dto.UpdateUserStatusRequest;
import com.argus.rag.user.model.vo.AdminUserItemResponse;
import com.argus.rag.user.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员用户管理控制器，仅系统管理员可访问。
 */
@OperationLog
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final CurrentUserService currentUserService;
    private final AdminUserService adminUserService;

    public AdminUserController(
            CurrentUserService currentUserService,
            AdminUserService adminUserService
    ) {
        this.currentUserService = currentUserService;
        this.adminUserService = adminUserService;
    }

    /** 获取全部用户列表 */
    @GetMapping
    public ApiResponse<List<AdminUserItemResponse>> listUsers() {
        currentUserService.requireSystemAdmin();
        return ApiResponse.success(adminUserService.listUsers());
    }

    /** 根据 ID 获取单个用户 */
    @GetMapping("/{userId}")
    public ApiResponse<AdminUserItemResponse> getUser(@PathVariable Long userId) {
        currentUserService.requireSystemAdmin();
        return ApiResponse.success(adminUserService.getUser(userId));
    }

    /** 修改用户状态（启用/禁用） */
    @PatchMapping("/{userId}/status")
    public ApiResponse<Void> updateUserStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        currentUserService.requireSystemAdmin();
        adminUserService.updateUserStatus(userId, request);
        return ApiResponse.success(null);
    }

}

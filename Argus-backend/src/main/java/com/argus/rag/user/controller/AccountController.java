package com.argus.rag.user.controller;

import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.common.api.ApiResponse;
import com.argus.rag.user.model.dto.ChangePasswordRequest;
import com.argus.rag.user.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人账户控制器，提供修改密码等接口。
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;
    private final CurrentUserService currentUserService;

    public AccountController(AccountService accountService, CurrentUserService currentUserService) {
        this.accountService = accountService;
        this.currentUserService = currentUserService;
    }

    /** 修改当前登录用户的密码 */
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                            HttpServletRequest httpServletRequest) {
        accountService.changePassword(currentUserService.getRequiredCurrentUser(httpServletRequest), request);
        return ApiResponse.success(null);
    }
}

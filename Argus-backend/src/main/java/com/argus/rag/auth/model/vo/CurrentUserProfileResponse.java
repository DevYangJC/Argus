package com.argus.rag.auth.model.vo;

import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.common.enums.SystemRole;

/**
 * 当前用户信息响应。
 */
public record CurrentUserProfileResponse(
        Long userId,
        String userCode,
        String displayName,
        SystemRole systemRole,
        boolean mustChangePassword
) {

    public static CurrentUserProfileResponse from(CurrentUserService.CurrentUser currentUser) {
        return new CurrentUserProfileResponse(
                currentUser.userId(),
                currentUser.userCode(),
                currentUser.displayName(),
                currentUser.systemRole(),
                currentUser.mustChangePassword()
        );
    }
}

package com.argus.rag.auth.model.vo;

import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.auth.service.AuthService.AuthTokens;

/**
 * 登录/刷新令牌响应，包含 access token 和当前用户信息。
 * <p>
 * Refresh token 通过 Cookie 下发，不在此响应中。
 */
public record AuthTokensResponse(
        String accessToken,
        CurrentUserProfileResponse currentUser
) {

    public static AuthTokensResponse from(AuthTokens tokens, CurrentUserService.CurrentUser currentUser) {
        return new AuthTokensResponse(tokens.accessToken(), CurrentUserProfileResponse.from(currentUser));
    }
}

package com.argus.rag.auth.controller;

import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.auth.config.AuthProperties;
import com.argus.rag.auth.model.dto.LoginRequest;
import com.argus.rag.auth.model.dto.RegisterRequest;
import com.argus.rag.auth.model.vo.AuthTokensResponse;
import com.argus.rag.auth.model.vo.CurrentUserProfileResponse;
import com.argus.rag.auth.security.AuthCookieSupport;
import com.argus.rag.auth.service.AuthService;
import com.argus.rag.auth.service.AuthService.AuthTokens;
import com.argus.rag.common.api.ApiResponse;
import com.argus.rag.common.log.OperationLog;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器，提供登录、注册、刷新令牌、登出、获取当前用户等接口。
 * <p>
 * 刷新令牌通过 httpOnly Cookie 下发，前端无需手动处理。
 */
@RestController
@RequestMapping("/api/auth")

public class AuthController {

    private final AuthService authService;
    private final AuthCookieSupport authCookieSupport;
    private final CurrentUserService currentUserService;
    private final AuthProperties authProperties;

    public AuthController(
            AuthService authService,
            AuthCookieSupport authCookieSupport,
            CurrentUserService currentUserService,
            AuthProperties authProperties
    ) {
        this.authService = authService;
        this.authCookieSupport = authCookieSupport;
        this.currentUserService = currentUserService;
        this.authProperties = authProperties;
    }

    /** 登录：返回 access token，同时通过 Cookie 下发 refresh token */
    @PostMapping("/login")
    @OperationLog
    public ApiResponse<AuthTokensResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthTokens tokens = authService.login(request.loginId(), request.password());
        authCookieSupport.writeRefreshTokenCookie(response, tokens.refreshToken());
        return ApiResponse.success(AuthTokensResponse.from(tokens));
    }

    /** 注册新用户 */
    @PostMapping("/register")
    @OperationLog
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.success(null);
    }

    /** 刷新令牌：从 Cookie 读取 refresh token，返回新的 access token 和 refresh token */
    @PostMapping("/refresh")
    public ApiResponse<AuthTokensResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refreshToken = extractRefreshToken(request);
        AuthTokens tokens = authService.refresh(refreshToken);
        authCookieSupport.writeRefreshTokenCookie(response, tokens.refreshToken());
        return ApiResponse.success(AuthTokensResponse.from(tokens));
    }

    /** 登出：吊销 refresh token 并清除 Cookie */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        authService.logout(extractRefreshToken(request));
        authCookieSupport.clearRefreshTokenCookie(response);
        return ApiResponse.success(null);
    }

    /** 获取当前登录用户信息 */
    @GetMapping("/me")
    public ApiResponse<CurrentUserProfileResponse> currentUser() {
        return ApiResponse.success(CurrentUserProfileResponse.from(currentUserService.getRequiredCurrentUser()));
    }

    private String extractRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        String refreshCookieName = authProperties.getRefreshCookieName();
        for (Cookie cookie : cookies) {
            if (refreshCookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}

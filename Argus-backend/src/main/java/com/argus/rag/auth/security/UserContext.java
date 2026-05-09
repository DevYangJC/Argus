package com.argus.rag.auth.security;

/**
 * 请求级用户上下文，基于 ThreadLocal 实现。
 * <p>
 * 由 {@link JwtAuthenticationFilter} 在请求进入时设置，请求结束时清理。
 * 业务代码任意位置通过 {@link #get()} 静态方法获取当前用户，无需传参。
 */
public final class UserContext {

    private static final ThreadLocal<JwtAuthenticationFilter.AuthenticatedUser> CURRENT_USER = new ThreadLocal<>();

    private UserContext() {
    }

    /** 设置当前请求的用户信息（由 Filter 调用） */
    public static void set(JwtAuthenticationFilter.AuthenticatedUser user) {
        CURRENT_USER.set(user);
    }

    /** 获取当前请求的用户信息，未认证时返回 null */
    public static JwtAuthenticationFilter.AuthenticatedUser get() {
        return CURRENT_USER.get();
    }

    /** 清理当前线程的用户信息（由 Filter 在 finally 中调用，防止内存泄漏和数据污染） */
    public static void clear() {
        CURRENT_USER.remove();
    }
}

package com.argus.rag.auth;

import com.argus.rag.auth.security.JwtAuthenticationFilter;
import com.argus.rag.auth.security.UserContext;
import com.argus.rag.common.enums.SystemRole;
import com.argus.rag.common.enums.UserStatus;
import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.common.exception.ForbiddenException;
import com.argus.rag.common.exception.UnauthorizedException;
import com.argus.rag.user.mapper.UserMapper;
import com.argus.rag.user.model.entity.User;
import org.springframework.stereotype.Service;

/**
 * 当前用户服务，通过 {@link UserContext} 获取当前请求用户。
 * <p>
 * 依赖 {@link JwtAuthenticationFilter} 在请求进入时设置 {@link UserContext}。
 */
@Service
public class CurrentUserService {

    private final UserMapper userMapper;

    public CurrentUserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 获取当前登录用户，未登录抛出 401 */
    public CurrentUser getRequiredCurrentUser() {
        JwtAuthenticationFilter.AuthenticatedUser authenticatedUser = UserContext.get();
        if (authenticatedUser != null) {
            return loadUserById(authenticatedUser.userId());
        }
        throw new UnauthorizedException("当前请求未登录");
    }

    /** 要求当前用户为系统管理员，否则抛出 403 */
    public CurrentUser requireSystemAdmin() {
        CurrentUser currentUser = getRequiredCurrentUser();
        if (currentUser.systemRole() != SystemRole.ADMIN) {
            throw new ForbiddenException("当前用户不是系统管理员");
        }
        return currentUser;
    }

    /** 要求当前用户为业务用户（非管理员），否则抛出 403 */
    public CurrentUser requireBusinessUser() {
        CurrentUser currentUser = getRequiredCurrentUser();
        if (currentUser.systemRole() == SystemRole.ADMIN) {
            throw new ForbiddenException("系统管理员不能访问普通业务区");
        }
        return currentUser;
    }

    /** 从数据库加载用户并校验状态 */
    private CurrentUser loadUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("当前用户不存在");
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BusinessException("账号已被禁用");
        }
        return new CurrentUser(
                user.getId(),
                user.getUserCode(),
                user.getDisplayName(),
                user.getSystemRole(),
                Boolean.TRUE.equals(user.getMustChangePassword())
        );
    }

    /** 当前登录用户信息 */
    public record CurrentUser(
            Long userId,
            String userCode,
            String displayName,
            SystemRole systemRole,
            boolean mustChangePassword
    ) {
        public CurrentUser(Long userId, String userCode, String displayName) {
            this(userId, userCode, displayName, SystemRole.USER, false);
        }
    }
}

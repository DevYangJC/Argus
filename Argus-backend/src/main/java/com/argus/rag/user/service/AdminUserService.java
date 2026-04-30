package com.argus.rag.user.service;

import com.argus.rag.auth.security.RefreshTokenService;
import com.argus.rag.common.enums.UserStatus;
import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.user.mapper.UserMapper;
import com.argus.rag.user.model.dto.UpdateUserStatusRequest;
import com.argus.rag.user.model.entity.User;
import com.argus.rag.user.model.vo.AdminUserItemResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 管理员用户管理服务。
 */
@Service
public class AdminUserService {

    private final UserMapper userMapper;
    private final RefreshTokenService refreshTokenService;
    private final UserQueryService userQueryService;

    public AdminUserService(
            UserMapper userMapper,
            RefreshTokenService refreshTokenService,
            UserQueryService userQueryService
    ) {
        this.userMapper = userMapper;
        this.refreshTokenService = refreshTokenService;
        this.userQueryService = userQueryService;
    }

    /** 获取所有用户列表 */
    public List<AdminUserItemResponse> listUsers() {
        return userQueryService.listUsers();
    }

    /** 获取单个用户 */
    public AdminUserItemResponse getUser(Long userId) {
        return userQueryService.getUser(requireUserId(userId));
    }

    /** 修改用户状态，禁用时同时吊销其所有 refresh token */
    @Transactional
    public void updateUserStatus(Long userId, UpdateUserStatusRequest request) {
        User user = userMapper.selectById(requireUserId(userId));
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setStatus(request.status());
        userMapper.updateById(user);
        if (request.status() == UserStatus.DISABLED) {
            refreshTokenService.revokeActiveTokens(userId);
        }
    }

    private Long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("用户ID非法");
        }
        return userId;
    }
}

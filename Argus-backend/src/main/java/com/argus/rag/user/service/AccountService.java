package com.argus.rag.user.service;

import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.auth.security.RefreshTokenService;
import com.argus.rag.auth.service.PasswordHasher;
import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.user.mapper.UserMapper;
import com.argus.rag.user.model.dto.ChangePasswordRequest;
import com.argus.rag.user.model.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 个人账户服务，处理修改密码等。
 */
@Service
public class AccountService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserMapper userMapper;
    private final PasswordHasher passwordHasher;
    private final RefreshTokenService refreshTokenService;

    public AccountService(UserMapper userMapper, PasswordHasher passwordHasher, RefreshTokenService refreshTokenService) {
        this.userMapper = userMapper;
        this.passwordHasher = passwordHasher;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * 修改密码：校验当前密码正确性 + 新密码复杂度，更新后吊销所有 refresh token。
     */
    @Transactional
    public void changePassword(CurrentUserService.CurrentUser currentUser, ChangePasswordRequest request) {
        validatePasswordPolicy(request.newPassword());
        User user = userMapper.selectById(currentUser.userId());
        if (user == null || user.getPasswordHash() == null) {
            throw new BusinessException("用户不存在");
        }
        if (!passwordHasher.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("当前密码不正确");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException("新密码不能与当前密码相同");
        }
        user.setPasswordHash(passwordHasher.hash(request.newPassword()));
        user.setMustChangePassword(false);
        int updated = userMapper.updateById(user);
        if (updated == 0) {
            throw new BusinessException("用户不存在");
        }
        refreshTokenService.revokeActiveTokens(currentUser.userId());
    }

    /** 密码策略：至少 8 位，包含字母和数字 */
    private void validatePasswordPolicy(String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException("新密码必须至少 8 位，且同时包含字母和数字");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < newPassword.length(); i++) {
            char current = newPassword.charAt(i);
            if (Character.isLetter(current)) {
                hasLetter = true;
            }
            if (Character.isDigit(current)) {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            throw new BusinessException("新密码必须至少 8 位，且同时包含字母和数字");
        }
    }
}

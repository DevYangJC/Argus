package com.argus.rag.user.service;

import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.auth.security.RefreshTokenService;
import com.argus.rag.auth.service.PasswordHasher;
import com.argus.rag.auth.service.PasswordPolicyValidator;
import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.user.mapper.UserMapper;
import com.argus.rag.user.model.dto.ChangePasswordRequest;
import com.argus.rag.user.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 个人账户服务，处理修改密码等。
 */
@Slf4j
@Service
public class AccountService {

    private final UserMapper userMapper;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicyValidator passwordValidator;
    private final RefreshTokenService refreshTokenService;

    public AccountService(UserMapper userMapper, PasswordHasher passwordHasher,
                          PasswordPolicyValidator passwordValidator, RefreshTokenService refreshTokenService) {
        this.userMapper = userMapper;
        this.passwordHasher = passwordHasher;
        this.passwordValidator = passwordValidator;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * 修改密码：校验当前密码正确性 + 新密码复杂度，更新后吊销所有 refresh token。
     */
    @Transactional
    public void changePassword(CurrentUserService.CurrentUser currentUser, ChangePasswordRequest request) {
        passwordValidator.validate(request.newPassword());
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
        log.info("密码修改成功: userId={}", currentUser.userId());
    }
}

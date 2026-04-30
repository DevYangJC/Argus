package com.argus.rag.auth.service;

import com.argus.rag.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 密码策略校验：最小长度、字母+数字组合、BCrypt 字节上限。
 * <p>
 * 供 {@link AuthService} 注册和 {@link AccountService} 修改密码共用。
 */
@Component
public class PasswordPolicyValidator {

    static final String INVALID_MESSAGE = "新密码必须至少 8 位，且同时包含字母和数字";
    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 256;
    /** BCrypt 最大输入字节数，超出会截断 */
    private static final int MAX_BYTES = 72;

    /** 校验密码复杂度并检查 BCrypt 字节上限 */
    public void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new BusinessException(INVALID_MESSAGE);
        }
        if (password.length() > MAX_LENGTH) {
            throw new BusinessException("密码长度非法");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new BusinessException("密码长度超过安全上限，请控制在 72 字节以内");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char current = password.charAt(i);
            if (Character.isLetter(current)) {
                hasLetter = true;
            }
            if (Character.isDigit(current)) {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            throw new BusinessException(INVALID_MESSAGE);
        }
    }
}

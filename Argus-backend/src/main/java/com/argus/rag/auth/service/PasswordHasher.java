package com.argus.rag.auth.service;

/**
 * 密码哈希接口，默认使用 BCrypt 实现。
 */
public interface PasswordHasher {

    /** 对明文密码做哈希 */
    String hash(String rawPassword);

    /** 校验明文密码是否匹配已存储的哈希值 */
    boolean matches(String rawPassword, String passwordHash);
}

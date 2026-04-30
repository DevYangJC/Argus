package com.argus.rag.auth.config;

import com.argus.rag.auth.service.PasswordHasher;
import com.argus.rag.common.enums.SystemRole;
import com.argus.rag.common.enums.UserStatus;
import com.argus.rag.user.mapper.UserMapper;
import com.argus.rag.user.model.entity.User;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 仅在 dev 环境确保一个可预测的管理员账号存在，避免本地调试还要额外手工开户。
 * <p>
 * 通过 {@code ddrag.dev-admin.*} 配置项自定义账号信息。
 */
@Slf4j
@Component
@Profile("dev")
public class DevAdminInitializer implements ApplicationRunner {

    private final UserMapper userMapper;
    private final PasswordHasher passwordHasher;
    private final String username;
    private final String email;
    private final String displayName;
    private final String password;
    private final String userCode;

    public DevAdminInitializer(
            UserMapper userMapper,
            PasswordHasher passwordHasher,
            @Value("${ddrag.dev-admin.username:admin}") String username,
            @Value("${ddrag.dev-admin.email:admin@local.ddrag.test}") String email,
            @Value("${ddrag.dev-admin.display-name:开发环境管理员}") String displayName,
            @Value("${ddrag.dev-admin.password:Admin@123456}") String password,
            @Value("${ddrag.dev-admin.user-code:admin}") String userCode
    ) {
        this.userMapper = userMapper;
        this.passwordHasher = passwordHasher;
        this.username = username.trim();
        this.email = email.trim();
        this.displayName = displayName.trim();
        this.password = password;
        this.userCode = userCode.trim();
    }

    /** 启动时检查管理员账号，不存在则创建，存在则更新密码 */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User existing = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
                        .last("order by id limit 1")
        );
        if (existing == null) {
            User user = new User();
            user.setUserCode(userCode);
            user.setUsername(username);
            user.setEmail(email);
            user.setDisplayName(displayName);
            user.setPasswordHash(passwordHasher.hash(password));
            user.setSystemRole(SystemRole.ADMIN);
            user.setStatus(UserStatus.ACTIVE);
            user.setMustChangePassword(false);
            userMapper.insert(user);
            log.info("Dev admin initialized. username={}", username);
            return;
        }
        existing.setEmail(email);
        existing.setDisplayName(displayName);
        existing.setPasswordHash(passwordHasher.hash(password));
        existing.setSystemRole(SystemRole.ADMIN);
        existing.setStatus(UserStatus.ACTIVE);
        existing.setMustChangePassword(false);
        userMapper.updateById(existing);
        log.info("Dev admin refreshed. username={}", username);
    }
}

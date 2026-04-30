package com.argus.rag.user.service;

import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.user.mapper.UserMapper;
import com.argus.rag.user.model.entity.User;
import com.argus.rag.user.model.vo.AdminUserItemResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户查询服务，提供对 users 表的通用查询。
 */
@Service
public class UserQueryService {

    private final UserMapper userMapper;

    public UserQueryService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 按 ID 升序返回所有用户 */
    public List<AdminUserItemResponse> listUsers() {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().orderByAsc(User::getId)
        );
        return users.stream().map(UserQueryService::toAdminVo).toList();
    }

    /** 按 ID 查询单个用户，不存在则抛出异常 */
    public AdminUserItemResponse getUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return toAdminVo(user);
    }

    /** 判断用户名是否已存在 */
    public boolean existsByUsername(String username) {
        return userMapper.exists(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
    }

    /** 判断邮箱是否已存在 */
    public boolean existsByEmail(String email) {
        return userMapper.exists(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email)
        );
    }

    /** 将 User 实体转为管理端 VO */
    private static AdminUserItemResponse toAdminVo(User user) {
        return new AdminUserItemResponse(
                user.getId(),
                user.getUserCode(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getSystemRole(),
                user.getStatus(),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                user.getLastLoginAt()
        );
    }
}

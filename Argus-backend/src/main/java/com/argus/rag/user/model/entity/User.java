package com.argus.rag.user.model.entity;

import com.argus.rag.common.enums.SystemRole;
import com.argus.rag.common.enums.UserStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，映射 users 表。
 */
@TableName("users")
@Data
public class User {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户编码，前端展示用，不可修改 */
    private String userCode;

    /** 登录用户名，唯一 */
    private String username;

    /** 邮箱，唯一 */
    private String email;

    /** 显示名称 */
    private String displayName;

    /** BCrypt 密码哈希 */
    private String passwordHash;

    /** 系统角色 */
    private SystemRole systemRole;

    /** 账号状态 */
    private UserStatus status;

    /** 是否强制修改密码 */
    private Boolean mustChangePassword;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}

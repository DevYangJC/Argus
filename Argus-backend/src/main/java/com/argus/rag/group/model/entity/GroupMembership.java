package com.argus.rag.group.model.entity;

import com.argus.rag.common.enums.GroupRole;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群组成员关系实体，映射 group_memberships 表。
 */
@TableName("group_memberships")
@Data
public class GroupMembership {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 群组 ID */
    private Long groupId;

    /** 用户 ID */
    private Long userId;

    /** 群组角色 */
    private GroupRole role;

    /** 加入时间 */
    private LocalDateTime createdAt;
}

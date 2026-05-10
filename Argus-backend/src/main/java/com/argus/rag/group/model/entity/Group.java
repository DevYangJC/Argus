package com.argus.rag.group.model.entity;

import com.argus.rag.common.enums.GroupStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群组实体，映射 groups 表。
 */
@TableName("groups")
@Data
public class Group {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 群组编码，对外展示用，不可修改 */
    private String groupCode;

    /** 群组名称 */
    private String groupName;

    /** 群组描述 */
    private String description;

    /** 创建者用户 ID */
    private Long ownerUserId;

    /** 群组状态 */
    private GroupStatus status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}

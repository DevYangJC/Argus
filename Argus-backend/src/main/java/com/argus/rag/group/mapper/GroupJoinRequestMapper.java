package com.argus.rag.group.mapper;

import com.argus.rag.group.model.entity.Group;
import com.argus.rag.group.model.entity.GroupJoinRequest;
import com.argus.rag.group.model.vo.MyJoinRequestResponse;
import com.argus.rag.group.model.vo.OwnerJoinRequestResponse;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 群组加入申请 Mapper，提供加入申请、群组查询相关的数据库操作。
 */
@Mapper
public interface GroupJoinRequestMapper extends BaseMapper<GroupJoinRequest> {

    /** 根据群组编码查询活跃群组 */
    Group selectActiveGroupByCode(@Param("groupCode") String groupCode);

    /** 统计用户在指定群组中的活跃成员数量 */
    Long countActiveMembership(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /** 统计指定群组中对指定用户的待处理邀请数 */
    Long countPendingInvitation(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /** 统计指定群组中指定用户的待处理加入申请数 */
    Long countPendingJoinRequest(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /** 插入待处理加入申请并返回自增主键 */
    Long insertPendingJoinRequestReturningId(@Param("groupId") Long groupId, @Param("applicantUserId") Long applicantUserId, @Param("status") String status);

    /** 查询当前用户的所有加入申请列表 */
    List<MyJoinRequestResponse> selectMyJoinRequests(@Param("applicantUserId") Long applicantUserId);

    /** 查询指定群组的待处理加入申请列表 */
    List<OwnerJoinRequestResponse> selectPendingJoinRequestsByGroupId(@Param("groupId") Long groupId);

    /** 根据 ID 查询加入申请记录 */
    GroupJoinRequest selectJoinRequestById(@Param("requestId") Long requestId);

    /** 更新加入申请状态（乐观锁：仅当当前状态匹配 fromStatus 时才更新） */
    int updateJoinRequestStatus(@Param("requestId") Long requestId, @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus, @Param("decidedByUserId") Long decidedByUserId);

    /** 插入成员关系记录 */
    int insertMembership(@Param("groupId") Long groupId, @Param("userId") Long userId, @Param("role") String role);
}

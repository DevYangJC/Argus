package com.argus.rag.auth.mapper;

import com.argus.rag.auth.model.entity.UserRefreshToken;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface UserRefreshTokenMapper extends BaseMapper<UserRefreshToken> {

    /** 原子吊销：仅当 revoked_at 为 null 时才更新，返回受影响行数用于检测并发重放 */
    @Update("update user_refresh_tokens set revoked_at = #{revokedAt} where id = #{id} and revoked_at is null")
    int revokeByIdIfActive(@Param("id") Long id, @Param("revokedAt") LocalDateTime revokedAt);
}

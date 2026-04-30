package com.argus.rag.auth.mapper;

import com.argus.rag.auth.model.entity.UserRefreshToken;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserRefreshTokenMapper extends BaseMapper<UserRefreshToken> {
}

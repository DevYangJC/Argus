package com.argus.rag.user.mapper;

import com.argus.rag.user.model.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 登录时根据用户名或邮箱查找用户，加行锁 */
    List<User> selectByLoginIdForUpdate(@Param("loginId") String loginId);

    /** 根据用户名和邮箱查找用户，加行锁 */
    List<User> selectByUsernameAndEmailForUpdate(@Param("username") String username,
                                                  @Param("email") String email);
}

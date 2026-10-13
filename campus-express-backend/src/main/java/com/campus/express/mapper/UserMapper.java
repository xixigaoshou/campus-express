package com.campus.express.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.express.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户表 Mapper（t_user）
 * 基础增删改查由 BaseMapper 提供，自定义：登录查询、信誉分更新
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 登录查询：按学号或手机号查用户（学号/手机号均唯一）
     * 供 AuthService 校验密码用
     */
    User selectByLoginName(@Param("loginName") String loginName);

    /**
     * 信誉分更新（规则 R-22/R-23 动态计算）
     * 乐观更新：score + delta 后必须仍 >= 0，避免负分；同时以 maxScore 封顶，达到上限后好评不再加分
     *
     * @param userId   目标用户
     * @param delta    变化量：好评 +1 / 差评 -2 / 违约取消 -5
     * @param maxScore 信誉分上限（封顶值）
     */
    int updateCreditScore(@Param("userId") Integer userId, @Param("delta") int delta,
                          @Param("maxScore") int maxScore);
}

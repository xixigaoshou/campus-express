package com.campus.express.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.express.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 站内通知表 Mapper（t_notification，非 5 张核心表）
 * 通知全部使用站内消息实现（硬性约束第 2 条）
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /** 查询用户通知列表（新到旧） */
    List<Notification> selectByUserId(@Param("userId") Integer userId);

    /** 统计用户未读通知数 */
    int countUnread(@Param("userId") Integer userId);

    /** 将该用户全部通知标记已读 */
    int markAllRead(@Param("userId") Integer userId);
}

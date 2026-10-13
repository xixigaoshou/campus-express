package com.campus.express.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.express.entity.Notification;

import java.util.List;

/**
 * 站内通知服务（规则 R-5 / R-14 ~ R-16）
 * 通知全部使用站内消息实现，不接入第三方
 */
public interface NotificationService {

    /** 发送站内通知 */
    void send(Integer userId, Integer orderId, String content);

    /** 查询用户通知列表（新到旧） */
    List<Notification> listByUser(Integer userId);

    /**
     * 分页查询用户通知列表（新到旧）
     *
     * @param isRead 可空：null 查全部；0 只看未读；1 只看已读
     */
    IPage<Notification> pageByUser(Integer userId, int pageNum, int pageSize, Integer isRead);

    /** 未读通知数（页面角标） */
    int countUnread(Integer userId);

    /** 单条通知标记已读（仅允许本人操作自己的通知） */
    void markRead(Integer notificationId, Integer userId);

    /** 全部标记已读 */
    void markAllRead(Integer userId);

    /** R-14 幂等判断：指定订单是否已发送过"无人接单"提醒（定时任务避免每轮扫描重复刷屏） */
    boolean hasNoTakerReminder(Integer orderId);

    /** 催单防刷：最近 10 分钟内是否已向该用户（接单者）发送过"发布者提醒"（避免频繁打扰） */
    boolean recentlyUrged(Integer userId, Integer orderId);
}

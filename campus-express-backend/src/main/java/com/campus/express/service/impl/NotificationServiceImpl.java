package com.campus.express.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.express.common.BusinessException;
import com.campus.express.entity.Notification;
import com.campus.express.mapper.NotificationMapper;
import com.campus.express.service.NotificationService;
import com.campus.express.ws.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内通知服务实现（规则 R-5 / R-14 ~ R-16）
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final WebSocketSessionManager webSocketSessionManager;

    /** 发送站内通知（入库后 WebSocket 实时推送信号给接收方刷新角标） */
    @Override
    public void send(Integer userId, Integer orderId, String content) {
        if (userId == null) {
            return;
        }
        Notification n = new Notification();
        n.setUserId(userId);
        n.setOrderId(orderId);
        n.setContent(content);
        n.setIsRead(0);
        notificationMapper.insert(n);
        webSocketSessionManager.sendToUser(userId, "new_notification");
    }

    /** 查询用户通知列表 */
    @Override
    public List<Notification> listByUser(Integer userId) {
        return notificationMapper.selectByUserId(userId);
    }

    /** 分页查询用户通知（新到旧，可选按已读状态过滤） */
    @Override
    public IPage<Notification> pageByUser(Integer userId, int pageNum, int pageSize, Integer isRead) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreateTime, Notification::getNotificationId);
        if (isRead != null) {
            wrapper.eq(Notification::getIsRead, isRead);
        }
        return notificationMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /** 未读通知数 */
    @Override
    public int countUnread(Integer userId) {
        return notificationMapper.countUnread(userId);
    }

    /** 单条通知标记已读：带 userId 条件，防止越权操作他人通知 */
    @Override
    public void markRead(Integer notificationId, Integer userId) {
        int rows = notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getNotificationId, notificationId)
                        .eq(Notification::getUserId, userId)
                        .set(Notification::getIsRead, 1));
        if (rows == 0) {
            throw new BusinessException("通知不存在或无权操作");
        }
    }

    /** 全部标记已读 */
    @Override
    public void markAllRead(Integer userId) {
        notificationMapper.markAllRead(userId);
    }

    /** R-14 幂等判断：该订单已存在"无人接单"提醒则不重复发送 */
    @Override
    public boolean hasNoTakerReminder(Integer orderId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getOrderId, orderId)
                .like(Notification::getContent, "无人接单")) > 0;
    }

    /** 催单防刷：最近 10 分钟内已向该用户该订单发送过"发布者提醒"则返回 true */
    @Override
    public boolean recentlyUrged(Integer userId, Integer orderId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getOrderId, orderId)
                .like(Notification::getContent, "发布者提醒")
                .ge(Notification::getCreateTime, LocalDateTime.now().minusMinutes(10))) > 0;
    }
}

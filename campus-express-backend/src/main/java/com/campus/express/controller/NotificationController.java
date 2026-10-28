package com.campus.express.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.express.common.Result;
import com.campus.express.entity.Notification;
import com.campus.express.entity.User;
import com.campus.express.service.NotificationService;
import com.campus.express.service.UserService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知接口（/api/notification）
 * 通知中心：分页列表 / 未读数 / 标记已读（单条与全部），规则 R-5 / R-14 ~ R-16
 * 权限：全部需登录，仅能操作本人通知
 */
@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    /**
     * 分页查询当前用户通知列表（新到旧）
     * 权限：已登录；isRead 可空：null 全部 / 0 未读 / 1 已读
     */
    @GetMapping("/page")
    public Result<IPage<Notification>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于 1") int pageNum,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数不能小于 1")
            @Max(value = 100, message = "每页条数不能超过 100") int pageSize,
            @RequestParam(required = false) Integer isRead) {
        User user = userService.getCurrentUser();
        return Result.success(notificationService.pageByUser(user.getUserId(), pageNum, pageSize, isRead));
    }

    /**
     * 当前用户未读通知数（导航栏角标）
     * 权限：已登录
     */
    @GetMapping("/unread-count")
    public Result<Integer> unreadCount() {
        User user = userService.getCurrentUser();
        return Result.success(notificationService.countUnread(user.getUserId()));
    }

    /**
     * 单条通知标记已读
     * 权限：已登录；仅允许本人操作自己的通知
     */
    @PutMapping("/read/{notificationId}")
    public Result<Void> markRead(@PathVariable Integer notificationId) {
        User user = userService.getCurrentUser();
        notificationService.markRead(notificationId, user.getUserId());
        return Result.success("已标记为已读", null);
    }

    /**
     * 全部通知标记已读
     * 权限：已登录；仅影响本人通知
     */
    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        User user = userService.getCurrentUser();
        notificationService.markAllRead(user.getUserId());
        return Result.success("已全部标记为已读", null);
    }
}

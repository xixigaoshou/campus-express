package com.campus.express.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.express.common.Result;
import com.campus.express.dto.SendMessageDTO;
import com.campus.express.service.MessageService;
import com.campus.express.vo.ConversationVO;
import com.campus.express.vo.MessageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单留言接口（/api/message）
 * 以订单为会话维度：发送 / 会话分页 / 已读标记 / 未读数角标
 * 全部需登录；仅订单参与者可发送与查看会话消息
 */
@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /**
     * 发送订单留言
     * 权限：已登录，且必须是订单发布者或接单者；内容经内置敏感词过滤，默认未读
     */
    @PostMapping("/send")
    public Result<Void> send(@RequestBody @Valid SendMessageDTO dto) {
        messageService.sendMessage(dto);
        return Result.success("发送成功", null);
    }

    /**
     * 获取该订单会话消息分页（按发送时间升序，MessageVO 带发送者昵称）
     * 权限：已登录，且为订单参与者；前端轮询此接口获取最新留言
     * @param orderId 订单 ID
     * @param pageNum 页码，从 1 开始
     * @param pageSize 每页条数
     */
    @GetMapping("/order-page/{orderId}")
    public Result<IPage<MessageVO>> orderPage(
            @PathVariable Integer orderId,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return Result.success(messageService.getOrderMessagePage(pageNum, pageSize, orderId));
    }

    /**
     * 将该订单下发给当前用户的消息全部标记为已读
     * 权限：已登录（本人接收的消息）
     * @param orderId 订单 ID
     */
    @PutMapping("/mark-read/{orderId}")
    public Result<Void> markRead(@PathVariable Integer orderId) {
        messageService.markRead(orderId);
        return Result.success("已全部标记为已读", null);
    }

    /**
     * 获取当前用户未读消息总数（个人订单中心角标）
     * 权限：已登录
     */
    @GetMapping("/unread-count")
    public Result<Integer> unreadCount() {
        return Result.success(messageService.countUnreadMsg());
    }

    /**
     * 获取当前用户参与的所有订单会话列表（含对方昵称、未读数、最新消息）
     * 消息抽屉展示：点会话进入对应订单详情，详情页标记已读后角标消失
     * 权限：已登录
     */
    @GetMapping("/conversations")
    public Result<java.util.List<ConversationVO>> conversations() {
        return Result.success(messageService.getConversations());
    }
}

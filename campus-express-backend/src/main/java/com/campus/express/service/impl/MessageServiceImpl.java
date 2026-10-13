package com.campus.express.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.express.common.BusinessException;
import com.campus.express.dto.SendMessageDTO;
import com.campus.express.entity.Message;
import com.campus.express.entity.Order;
import com.campus.express.entity.User;
import com.campus.express.mapper.MessageMapper;
import com.campus.express.mapper.OrderMapper;
import com.campus.express.service.MessageService;
import com.campus.express.service.UserService;
import com.campus.express.util.SensitiveWordUtil;
import com.campus.express.vo.ConversationVO;
import com.campus.express.vo.MessageVO;
import com.campus.express.ws.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 留言消息服务实现
 * 以订单为会话维度：仅订单参与者可留言；敏感词过滤；支持已读标记与未读数
 */
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;
    private final OrderMapper orderMapper;
    private final UserService userService;
    private final WebSocketSessionManager webSocketSessionManager;

    /** 发送订单留言（3.6）：参与者校验 + 敏感词过滤 + 默认未读 */
    @Override
    @Transactional
    public void sendMessage(SendMessageDTO dto) {
        User sender = userService.getCurrentUser();
        Order order = orderMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // 校验发送者是订单发布者或接单者（会话参与者）
        if (!isParticipant(order, sender.getUserId())) {
            throw new BusinessException(403, "仅订单发布者或接单者可留言");
        }
        // 接收者为另一方；待接单状态无接单者时不允许留言
        Integer receiverId = order.getPublisherId().equals(sender.getUserId())
                ? order.getTakerId() : order.getPublisherId();
        if (receiverId == null) {
            throw new BusinessException("订单尚未被接单，暂无留言对象");
        }

        Message message = new Message();
        message.setOrderId(order.getOrderId());
        message.setSenderId(sender.getUserId());
        message.setReceiverId(receiverId);
        // 简单内置敏感词过滤：命中词替换为 *（3.6）
        message.setContent(SensitiveWordUtil.filter(dto.getContent()));
        message.setIsRead(0); // 默认未读
        messageMapper.insert(message);

        // WebSocket 实时推送：通知接收方有新留言（前端收到后刷新未读角标）
        webSocketSessionManager.sendToUser(receiverId, "new_message");
    }

    /** 订单会话分页消息（时间升序），MessageVO 带发送者昵称（用户关联查询技术点） */
    @Override
    public IPage<MessageVO> getOrderMessagePage(long current, long size, Integer orderId) {
        User user = userService.getCurrentUser();
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // 会话隐私：仅参与者可查看该订单留言
        if (!isParticipant(order, user.getUserId())) {
            throw new BusinessException(403, "无权查看该订单的留言会话");
        }
        return messageMapper.selectByOrderPage(new Page<>(current, size), orderId);
    }

    /** 该订单下发给当前用户的消息全部标记已读 */
    @Override
    @Transactional
    public void markRead(Integer orderId) {
        User user = userService.getCurrentUser();
        messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getOrderId, orderId)
                .eq(Message::getReceiverId, user.getUserId())
                .eq(Message::getIsRead, 0)
                .set(Message::getIsRead, 1));
    }

    /** 当前用户全部未读消息数（页面角标） */
    @Override
    public int countUnreadMsg() {
        return messageMapper.countUnread(userService.getCurrentUser().getUserId());
    }

    /** 当前用户参与的所有订单会话列表（消息抽屉，按最新消息时间倒序） */
    @Override
    public List<ConversationVO> getConversations() {
        return messageMapper.selectConversations(userService.getCurrentUser().getUserId());
    }

    /** 判断用户是否为订单参与者（发布者或接单者） */
    private boolean isParticipant(Order order, Integer userId) {
        if (order.getPublisherId().equals(userId)) {
            return true;
        }
        return order.getTakerId() != null && order.getTakerId().equals(userId);
    }
}

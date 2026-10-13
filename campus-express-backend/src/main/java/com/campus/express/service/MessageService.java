package com.campus.express.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.express.dto.SendMessageDTO;
import com.campus.express.vo.ConversationVO;
import com.campus.express.vo.MessageVO;

import java.util.List;

/**
 * 留言消息服务（3.6 消息留言）
 * 以订单为会话维度：发送 / 分页会话 / 已读标记 / 未读数角标 / 会话列表
 */
public interface MessageService {

    /**
     * 发送订单留言：校验发送者是发布者或接单者；敏感词过滤；默认未读
     */
    void sendMessage(SendMessageDTO dto);

    /**
     * 订单会话分页消息（按发送时间升序），MessageVO 带发送者昵称
     * 前端轮询获取最新留言
     */
    IPage<MessageVO> getOrderMessagePage(long current, long size, Integer orderId);

    /**
     * 把该订单下发给当前用户的消息全部标记为已读
     */
    void markRead(Integer orderId);

    /**
     * 统计当前用户全部未读消息数（个人订单中心角标）
     */
    int countUnreadMsg();

    /**
     * 当前用户参与的所有订单会话列表（含对方昵称、未读数、最新消息）
     * 消息抽屉展示：点会话进入对应订单详情
     */
    List<ConversationVO> getConversations();
}

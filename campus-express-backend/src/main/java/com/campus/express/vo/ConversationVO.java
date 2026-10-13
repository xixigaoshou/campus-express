package com.campus.express.vo;

import lombok.Data;

/**
 * 订单会话视图对象：消息抽屉 / 会话列表展示用
 * 以订单为会话维度，聚合对方昵称、未读数与最新一条消息
 */
@Data
public class ConversationVO {

    /** 订单 ID（会话标识） */
    private Integer orderId;

    /** 对方（会话另一方）昵称 */
    private String otherName;

    /** 该订单下发给我方的未读消息数 */
    private Integer unreadCount;

    /** 最新一条消息内容（任意方向） */
    private String lastContent;

    /** 最新一条消息发送者昵称 */
    private String lastSenderName;

    /** 最新一条消息发送时间 */
    private String lastTime;
}

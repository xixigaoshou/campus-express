package com.campus.express.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 留言消息实体（对应表 t_message）
 * 以订单为会话维度：订单创建后自动生成留言会话（3.6 消息留言）
 * 字段与数据库表严格一一对应，禁止增减
 */
@Data
@TableName("t_message")
public class Message {

    /** 留言唯一标识，自增主键 */
    @TableId(type = IdType.AUTO)
    private Integer messageId;

    /** 所属订单 ID，关联 t_order（即会话 ID） */
    private Integer orderId;

    /** 发送者用户 ID */
    private Integer senderId;

    /** 接收者用户 ID */
    private Integer receiverId;

    /** 留言内容（发送前经敏感词过滤） */
    private String content;

    /** 是否已读：0 未读 1 已读（支持已读标记） */
    private Integer isRead;

    /** 发送时间 */
    private LocalDateTime createTime;
}

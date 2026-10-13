package com.campus.express.vo;

import com.campus.express.entity.Message;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 留言视图对象：留言字段 + 发送者昵称（用户关联查询技术点）
 * 订单留言会话时间线展示用
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MessageVO extends Message {

    /** 发送者昵称（JOIN t_user 关联查询） */
    private String senderName;
}

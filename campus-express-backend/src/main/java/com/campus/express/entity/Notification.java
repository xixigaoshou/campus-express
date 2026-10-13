package com.campus.express.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知实体（对应表 t_notification，非 5 张核心表）
 * 系统内部通知：关键状态变更时向相关方发送提醒（规则 R-5 / R-14 ~ R-16）
 */
@Data
@TableName("t_notification")
public class Notification {

    /** 通知唯一标识，自增主键 */
    @TableId(type = IdType.AUTO)
    private Integer notificationId;

    /** 接收通知的用户 ID */
    private Integer userId;

    /** 关联订单 ID（可为空） */
    private Integer orderId;

    /** 通知内容 */
    private String content;

    /** 是否已读：0 未读 1 已读 */
    private Integer isRead;

    /** 创建时间 */
    private LocalDateTime createTime;
}

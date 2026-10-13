package com.campus.express.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单状态历史实体（对应表 t_order_status_history）
 * 记录每一次状态变更轨迹，保证订单流转可追溯（规则 R-4）
 * 字段与数据库表严格一一对应，禁止增减
 */
@Data
@TableName("t_order_status_history")
public class OrderStatusHistory {

    /** 记录唯一标识，自增主键 */
    @TableId(type = IdType.AUTO)
    private Integer historyId;

    /** 订单 ID，关联 t_order */
    private Integer orderId;

    /** 变更前状态（首条记录可为空） */
    private String fromStatus;

    /** 变更后状态 */
    private String toStatus;

    /** 操作人用户 ID */
    private Integer operatorId;

    /** 变更说明 */
    private String changeNote;

    /** 变更时间 */
    private LocalDateTime changeTime;
}

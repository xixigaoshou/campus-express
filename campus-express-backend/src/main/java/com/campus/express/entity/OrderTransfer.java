package com.campus.express.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 转单接力记录实体（对应表 t_order_transfer）
 * 记录接单者发起的每次转单接力：原接单者、接力接单者、原因与状态
 * 订单当前是否在接力中以 t_order.transfer_status 为准，本表用于追溯与并发控制
 */
@Data
@TableName("t_order_transfer")
public class OrderTransfer {

    /** 转单记录唯一标识，自增主键 */
    @TableId(type = IdType.AUTO)
    private Integer transferId;

    /** 订单 ID，关联 t_order */
    private Integer orderId;

    /** 原接单者用户 ID，关联 t_user */
    private Integer fromTakerId;

    /** 接力接单者用户 ID，未接力前为空 */
    private Integer toTakerId;

    /** 转单原因 */
    private String reason;

    /** 转单状态：待接力/已接力/已撤回/已过期（见 TransferStatus） */
    private String status;

    /** 发起时间 */
    private LocalDateTime createTime;

    /** 结束时间（接力成功/撤回/过期） */
    private LocalDateTime finishTime;
}
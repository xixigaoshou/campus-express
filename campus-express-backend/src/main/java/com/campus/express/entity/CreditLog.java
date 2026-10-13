package com.campus.express.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 信誉分明细流水（对应表 t_credit_log）
 * 记录每次信誉分变化：评价好评/差评、恶意取消、接单超时，可追溯（R-22 扩展）
 */
@Data
@TableName("t_credit_log")
public class CreditLog {

    /** 流水ID，自增主键 */
    @TableId(type = IdType.AUTO)
    private Integer logId;

    /** 用户ID，关联 t_user */
    private Integer userId;

    /** 信誉分变化量：正=增加 负=减少 */
    private Integer changeAmount;

    /** 变化类型：评价好评/评价差评/接单者取消/接单超时 */
    private String changeType;

    /** 变化前信誉分 */
    private Integer beforeScore;

    /** 变化后信誉分 */
    private Integer afterScore;

    /** 说明（含关联订单号） */
    private String remark;

    /** 关联订单ID，可追溯来源 */
    private Integer relatedOrderId;

    /** 发生时间 */
    private LocalDateTime createTime;
}

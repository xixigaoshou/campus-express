package com.campus.express.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户钱包实体（对应表 t_wallet）
 * 虚拟钱包：可用余额 + 冻结余额（发布订单锁定报酬，R-26）；不接真实支付，仅模拟充值
 */
@Data
@TableName("t_wallet")
public class Wallet {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联用户 ID，唯一，外键 t_user */
    private Integer userId;

    /** 可用余额 */
    private BigDecimal balance;

    /** 冻结余额（发布订单锁定的报酬，冻结资金不可重复消费，R-26） */
    private BigDecimal frozenBalance;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 最近更新时间 */
    private LocalDateTime updateTime;
}

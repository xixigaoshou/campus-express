package com.campus.express.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包资金流水实体（对应表 t_wallet_log）
 * 所有资金操作（充值/冻结/解冻/结算）必须写入流水，保证可追溯（R-25 ~ R-28）
 */
@Data
@TableName("t_wallet_log")
public class WalletLog {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作用户 ID，外键 t_user */
    private Integer userId;

    /** 关联订单 ID，可为 null（充值场景） */
    private Integer relatedOrderId;

    /** 流水类型：1充值 2订单冻结 3解冻退回 4订单结算收入 5订单支出扣减（WalletLogType） */
    private Integer logType;

    /** 变动金额：正数增加，负数扣减 */
    private BigDecimal amount;

    /** 变动前可用余额 */
    private BigDecimal beforeBalance;

    /** 变动后可用余额 */
    private BigDecimal afterBalance;

    /** 变动前冻结金额 */
    private BigDecimal beforeFrozen;

    /** 变动后冻结金额 */
    private BigDecimal afterFrozen;

    /** 备注 */
    private String remark;

    /** 发生时间 */
    private LocalDateTime createTime;
}

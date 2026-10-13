package com.campus.express.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包资金流水返回对象（GET /api/wallet/log/page）
 * logTypeText 由枚举 code 转换：1充值 / 2订单报酬冻结 / 3订单解冻退回 / 4订单结算收入 / 5订单支出扣减
 */
@Data
public class WalletLogVO {

    /** 流水类型文本 */
    private String logTypeText;

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

    /** 关联订单 ID（充值场景为 null） */
    private Integer relatedOrderId;

    /** 备注 */
    private String remark;

    /** 发生时间 */
    private LocalDateTime createTime;
}

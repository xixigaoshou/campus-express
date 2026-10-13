package com.campus.express.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 钱包信息返回对象（GET /api/wallet/info）
 */
@Data
public class WalletVO {

    /** 关联用户 ID */
    private Integer userId;

    /** 可用余额 */
    private BigDecimal balance;

    /** 冻结余额（发布订单锁定的报酬，不可重复消费，R-26） */
    private BigDecimal frozenBalance;
}

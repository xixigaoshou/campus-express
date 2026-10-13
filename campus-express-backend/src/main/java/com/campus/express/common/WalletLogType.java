package com.campus.express.common;

import lombok.Getter;

/**
 * 钱包资金流水类型（对应 t_wallet_log.log_type）
 * 1 充值 / 2 订单报酬冻结 / 3 订单解冻退回 / 4 订单结算收入 / 5 订单支出扣减 / 6 提现
 */
@Getter
public enum WalletLogType {

    /** 模拟充值入账 */
    RECHARGE(1, "充值"),

    /** 发布订单时锁定报酬（R-26） */
    ORDER_FROZEN(2, "订单报酬冻结"),

    /** 订单取消，冻结报酬解冻退回（R-27） */
    ORDER_UNFROZEN(3, "订单解冻退回"),

    /** 订单完成，接单者报酬结算入账（R-28） */
    ORDER_INCOME(4, "订单结算收入"),

    /** 订单完成，发布者报酬支出扣减（R-28） */
    ORDER_PAY(5, "订单支出扣减"),

    /** 提现（模拟）：接单者将可用余额取出，资金流出 */
    WITHDRAW(6, "提现");

    private final int code;
    private final String text;

    WalletLogType(int code, String text) {
        this.code = code;
        this.text = text;
    }

    /** 按 code 查找枚举（非法值返回 null） */
    public static WalletLogType of(Integer code) {
        if (code == null) {
            return null;
        }
        for (WalletLogType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return null;
    }
}

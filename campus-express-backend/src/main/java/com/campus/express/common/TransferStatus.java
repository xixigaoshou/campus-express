package com.campus.express.common;

/**
 * 转单接力状态常量（存 t_order.transfer_status 与 t_order_transfer.status）
 * 说明：转单接力不改变订单状态机（R-1），仅作为订单的附加流转标记；
 * 接力中订单状态仍为「已接单/取件中」，只是接单者可被其他接单者接力替换
 */
public final class TransferStatus {

    private TransferStatus() {
    }

    /** 订单接力中，等待其他接单者接力 */
    public static final String PENDING = "待接力";

    /** 已被其他接单者接力承接 */
    public static final String DONE = "已接力";

    /** 原接单者主动撤回 */
    public static final String WITHDRAWN = "已撤回";

    /** 超时无人接力，系统自动撤回 */
    public static final String EXPIRED = "已过期";
}
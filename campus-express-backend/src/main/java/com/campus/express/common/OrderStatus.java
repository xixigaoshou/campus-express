package com.campus.express.common;

/**
 * 订单状态常量：状态机定义（规则 R-1 ~ R-5）
 * 正向流转：待接单 → 已接单 → 取件中 → 配送中 → 待收货 → 已完成
 * 待收货：接单者「确认送达」后进入；需求方「确认收货」或 24 小时超时自动确认后完成
 * 终态：已完成、已取消（待接单/已接单/取件中可取消，见 R-2）
 */
public final class OrderStatus {

    private OrderStatus() {
    }

    public static final String WAIT_ACCEPT = "待接单";
    public static final String ACCEPTED = "已接单";
    public static final String PICKING = "取件中";
    public static final String DELIVERING = "配送中";
    public static final String WAIT_CONFIRM = "待收货";
    public static final String COMPLETED = "已完成";
    public static final String CANCELLED = "已取消";

    /** 进行中的状态（个人订单中心"进行中"分类） */
    public static final String[] ACTIVE = {WAIT_ACCEPT, ACCEPTED, PICKING, DELIVERING, WAIT_CONFIRM};

    /** 取消操作合法的状态（规则 R-2：待接单/已接单/取件中） */
    public static final String[] CANCELABLE = {WAIT_ACCEPT, ACCEPTED, PICKING};
}

package com.campus.express.service;

/**
 * 超时调度领域服务：订单超时扫描与自动处理（每 60 秒触发）
 * 包含无人接单提醒、过期未接单自动取消、超时未取件退回、超时未送达终态取消、待收货超时自动确认收货
 */
public interface OrderTimeoutService {

    /**
     * 定时任务：超时提醒与自动取消（每 60 秒扫描一次）
     * ⓪待接单但期望取件时间已过 → 自动取消并解冻报酬
     * ①发布 waitAcceptHours 小时无人接单 → 提醒发布者调整（R-14，幂等）
     * ②已接单但超过期望取件时间仍未取件 → 自动取消并扣信誉分，退回待接单（R-15）
     * ③取件中/配送中超过期望取件时间仍未送达 → 自动取消（终态）、扣信誉分、解冻发布者报酬
     */
    void timeoutRemindCancelTask();

    /**
     * 定时任务：待收货超时自动确认收货（每 60 秒扫描一次）
     * 规则：接单者确认送达后 confirmReceiptHours（默认 24 小时）内需求方未确认 → 系统自动确认收货，报酬结算给接单者
     */
    void autoConfirmReceiptTask();
}
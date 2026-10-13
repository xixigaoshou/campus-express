package com.campus.express.service.impl;

import com.campus.express.entity.Order;
import com.campus.express.entity.OrderStatusHistory;

import java.time.LocalDateTime;

/**
 * 订单领域共享工具：被「订单流转 / 超时调度 / 转单接力」三个领域服务复用的纯函数
 * （状态历史记录构造、订单退回待接单的通知文案），避免同一逻辑在三处重复实现
 */
final class OrderDomainSupport {

    private OrderDomainSupport() {
    }

    /** 构建状态历史记录对象（R-4） */
    static OrderStatusHistory buildHistory(Integer orderId, String from, String to,
                                           Integer operatorId, String note) {
        OrderStatusHistory h = new OrderStatusHistory();
        h.setOrderId(orderId);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setOperatorId(operatorId);
        h.setChangeNote(note);
        h.setChangeTime(LocalDateTime.now());
        return h;
    }

    /**
     * 订单退回待接单后的通知文案：期望取件时间已过的订单不会展示在需求大厅、也无法被接单，
     * 需提示发布者修改取件时间后才能重新开放，避免「已退回待接单」的误导
     */
    static String reopenNotice(Order order, String base) {
        if (order.getExpectTime() != null && order.getExpectTime().isBefore(LocalDateTime.now())) {
            return base + "；因期望取件时间已过，订单暂不展示且无法被接单，请修改取件时间后重新开放";
        }
        return base;
    }
}
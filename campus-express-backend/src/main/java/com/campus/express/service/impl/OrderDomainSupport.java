package com.campus.express.service.impl;

import com.campus.express.entity.Order;
import com.campus.express.entity.OrderStatusHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * 订单领域共享工具：被「订单流转 / 超时调度 / 转单接力」三个领域服务复用的纯函数
 * （状态历史记录构造、订单退回待接单的通知文案），避免同一逻辑在三处重复实现
 */
final class OrderDomainSupport {

    private static final Logger log = LoggerFactory.getLogger(OrderDomainSupport.class);

    private OrderDomainSupport() {
    }

    /**
     * 在独立事务（REQUIRES_NEW）中执行单笔订单的定时处理：
     * 单笔失败只回滚该单，不影响同批其他订单，也不会让整批任务被一单坏数据永久卡死
     */
    static void runPerOrderTx(PlatformTransactionManager txManager, Integer orderId,
                              String scene, Runnable action) {
        TransactionTemplate template = new TransactionTemplate(txManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            template.executeWithoutResult(status -> action.run());
        } catch (Exception e) {
            log.error("[{}] 订单#{} 处理失败，已跳过该单（不影响其余订单）：{}", scene, orderId, e.getMessage(), e);
        }
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
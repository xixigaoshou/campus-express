package com.campus.express.service;

import com.campus.express.entity.OrderStatusHistory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单状态历史服务（规则 R-4：每次状态变更必须写入历史表）
 */
public interface OrderStatusHistoryService {

    /** 单条保存状态变更记录 */
    void saveHistoryRecord(OrderStatusHistory history);

    /** 批量保存状态变更记录 */
    void batchSaveHistory(List<OrderStatusHistory> historyList);

    /** 查询订单全部状态变更轨迹（时间升序） */
    List<OrderStatusHistory> listByOrderId(Integer orderId);

    /**
     * 统计某操作者在指定时间之后「主动取消订单」的次数（发布者取消频控用）
     * 仅统计用户主动取消（变更说明以「发布者取消」开头），不含系统超时自动取消
     */
    long countPublisherCancelSince(Integer operatorId, LocalDateTime since);
}

package com.campus.express.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.express.common.OrderStatus;
import com.campus.express.entity.OrderStatusHistory;
import com.campus.express.mapper.OrderStatusHistoryMapper;
import com.campus.express.service.OrderStatusHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单状态历史服务实现
 * 规则 R-4：每次状态变更必须同步写入历史表，形成完整流转轨迹
 */
@Service
@RequiredArgsConstructor
public class OrderStatusHistoryServiceImpl implements OrderStatusHistoryService {

    private final OrderStatusHistoryMapper historyMapper;

    /** 单条保存（MyBatis-Plus BaseMapper 自带 insert） */
    @Override
    public void saveHistoryRecord(OrderStatusHistory history) {
        historyMapper.insert(history);
    }

    /** 批量保存（自定义 batchInsert，MySQL 多 VALUES 一条 SQL） */
    @Override
    public void batchSaveHistory(List<OrderStatusHistory> historyList) {
        if (historyList == null || historyList.isEmpty()) {
            return;
        }
        historyMapper.batchInsert(historyList);
    }

    /** 查询订单完整状态流转轨迹 */
    @Override
    public List<OrderStatusHistory> listByOrderId(Integer orderId) {
        return historyMapper.selectByOrderId(orderId);
    }

    /** 统计用户主动取消（发布者取消）次数：to_status=已取消 且 说明以「发布者取消」开头 */
    @Override
    public long countPublisherCancelSince(Integer operatorId, LocalDateTime since) {
        return historyMapper.selectCount(new LambdaQueryWrapper<OrderStatusHistory>()
                .eq(OrderStatusHistory::getOperatorId, operatorId)
                .eq(OrderStatusHistory::getToStatus, OrderStatus.CANCELLED)
                .likeRight(OrderStatusHistory::getChangeNote, "发布者取消")
                .ge(OrderStatusHistory::getChangeTime, since));
    }
}

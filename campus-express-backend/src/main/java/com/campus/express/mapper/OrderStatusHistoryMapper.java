package com.campus.express.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.express.entity.OrderStatusHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单状态历史表 Mapper（t_order_status_history）
 * 记录每次状态变更轨迹（规则 R-4），保证订单流转可追溯
 */
@Mapper
public interface OrderStatusHistoryMapper extends BaseMapper<OrderStatusHistory> {

    /**
     * 批量插入状态变更记录（一次状态变更可能伴随多条通知场景，走批量）
     */
    int batchInsert(@Param("list") List<OrderStatusHistory> list);

    /**
     * 查询订单完整状态流转轨迹（按时间升序，订单详情页展示）
     */
    List<OrderStatusHistory> selectByOrderId(@Param("orderId") Integer orderId);
}

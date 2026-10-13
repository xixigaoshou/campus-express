package com.campus.express.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.express.entity.OrderTransfer;
import org.apache.ibatis.annotations.Mapper;

/**
 * 转单接力记录 Mapper（t_order_transfer）
 * 基础 CRUD 由 BaseMapper 提供，接力标记的读取以 t_order.transfer_status 为准
 */
@Mapper
public interface OrderTransferMapper extends BaseMapper<OrderTransfer> {
}
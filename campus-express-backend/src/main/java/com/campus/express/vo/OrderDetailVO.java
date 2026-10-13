package com.campus.express.vo;

import com.campus.express.entity.Evaluation;
import com.campus.express.entity.OrderStatusHistory;
import lombok.Data;

import java.util.List;

/**
 * 订单详情聚合视图：订单 + 完整状态流转记录 + 双向评价（3.7 订单详情）
 */
@Data
public class OrderDetailVO {

    /** 订单及关联用户昵称 */
    private OrderVO order;

    /** 完整状态流转轨迹（按时间升序） */
    private List<OrderStatusHistory> statusHistory;

    /** 双向评价列表（EvaluationVO 带双方昵称） */
    private List<EvaluationVO> evaluations;
}

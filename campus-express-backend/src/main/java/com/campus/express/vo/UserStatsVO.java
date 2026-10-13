package com.campus.express.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 个人中心统计聚合视图
 * 由后端全量聚合（COUNT / SUM），避免前端按分页 100 条上限统计导致口径失真
 */
@Data
public class UserStatsVO {

    /** 发布订单总数（publisher_id = 当前用户，全量） */
    private Long publishTotal;

    /** 发布订单取消数（publisher_id = 当前用户且状态为已取消） */
    private Long publishCancelled;

    /** 接单完成数（taker_id = 当前用户且状态为已完成） */
    private Long takeCompleted;

    /** 接单后取消次数（按信誉分流水「接单者取消」统计，退回待接单后订单已不在我的接单列表） */
    private Long takeCancelled;

    /** 累计收入（接单已完成订单报酬合计） */
    private BigDecimal income;

    /** 累计支出（发布已完成订单报酬合计） */
    private BigDecimal expense;

    /** 待评价数：我参与（发布或接单）的已完成订单中，尚未评价的数量 */
    private Long pendingEvaluation;
}
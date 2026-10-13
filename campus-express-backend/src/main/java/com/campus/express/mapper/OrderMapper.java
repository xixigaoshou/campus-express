package com.campus.express.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.express.entity.Order;
import com.campus.express.vo.OrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 订单表 Mapper（t_order）— 系统主业务表
 * 基础 CRUD 由 BaseMapper 提供，自定义：大厅分页、我的发布/接单、乐观锁抢单
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 需求大厅分页：全部"待接单"订单，按发布时间降序（3.8 需求列表浏览）
     * 关联用户表带出发布者昵称；支持关键词（快递公司/取件码/取件/送达/备注）与报酬范围筛选
     */
    IPage<OrderVO> selectHallPage(IPage<OrderVO> page,
                                  @Param("keyword") String keyword,
                                  @Param("minReward") java.math.BigDecimal minReward,
                                  @Param("maxReward") java.math.BigDecimal maxReward);

    /**
     * 订单详情：关联发布者/接单者昵称（多表关联查询技术点）
     */
    OrderVO selectOrderVOById(@Param("orderId") Integer orderId);

    /**
     * 我的发布/我的接单统一分页：publisherId 与 takerId 二选一，
     * statusList 为空查全部，非空按状态分组（进行中/历史）过滤（3.7）
     */
    IPage<OrderVO> selectMyPage(IPage<OrderVO> page,
                                @Param("publisherId") Integer publisherId,
                                @Param("takerId") Integer takerId,
                                @Param("statusList") java.util.List<String> statusList);

    /**
     * 乐观锁抢单更新（规则 R-7）
     * 更新条件：status='待接单' AND version=传入版本号
     * 影响行数 0 = 抢单失败；更新成功后 version 自动 +1
     */
    int optimisticTakeOrder(@Param("orderId") Integer orderId,
                            @Param("takerId") Integer takerId,
                            @Param("version") Integer version);

    /**
     * 接力专区分页：transfer_status='待接力' 且订单仍在「已接单/取件中」，按发起接力时间降序
     * 关联用户表带出原接单者昵称；支持关键词（快递公司/取件码/取件/送达/备注）与报酬范围筛选
     */
    IPage<OrderVO> selectRelayPage(IPage<OrderVO> page,
                                   @Param("keyword") String keyword,
                                   @Param("minReward") java.math.BigDecimal minReward,
                                   @Param("maxReward") java.math.BigDecimal maxReward);

    /**
     * 接力接单（乐观锁）：接单者由原接单者换为接力者，并清空接力标记
     * 更新条件：transfer_status='待接力' AND taker_id=原接单者 AND version=传入版本号
     * 影响行数 0 = 已被他人接力，接力失败
     */
    int relayTakeOrder(@Param("orderId") Integer orderId,
                       @Param("takerId") Integer takerId,
                       @Param("oldTakerId") Integer oldTakerId,
                       @Param("version") Integer version);

    /**
     * 累计收入：接单者指定状态订单的报酬合计（个人中心统计，全量 SUM 避免分页截断）
     */
    java.math.BigDecimal sumRewardByTaker(@Param("takerId") Integer takerId,
                                          @Param("status") String status);

    /**
     * 累计支出：发布者指定状态订单的报酬合计（个人中心统计，全量 SUM 避免分页截断）
     */
    java.math.BigDecimal sumRewardByPublisher(@Param("publisherId") Integer publisherId,
                                              @Param("status") String status);

    /**
     * 待评价数：我参与（发布或接单）的已完成订单中尚未评价的数量（个人中心「待评价」提示）
     */
    Long countPendingEvaluation(@Param("userId") Integer userId);
}

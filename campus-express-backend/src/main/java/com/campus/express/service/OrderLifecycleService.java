package com.campus.express.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.express.dto.OrderPublishDTO;
import com.campus.express.dto.OrderUpdateDTO;
import com.campus.express.vo.OrderDetailVO;
import com.campus.express.vo.OrderReuseVO;
import com.campus.express.vo.OrderVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单流转领域服务：需求发布/修改、抢单、履约状态机流转、取消、照片凭证与订单查询
 * （订单生命周期相关规则 R-1 ~ R-20）
 */
public interface OrderLifecycleService {

    /** 发布代取需求：生成待接单订单，初始化 version=0，写入首条状态历史（3.3） */
    Integer publishOrder(OrderPublishDTO dto);

    /** 修改待接单订单（R-11：仅待接单可改，已接单后禁止修改核心字段） */
    void updateWaitOrder(OrderUpdateDTO dto);

    /**
     * 再次发布模板：复制历史订单的包裹信息，用于一键快速发布
     * 权限：仅订单发布者本人；期望取件时间不预填（重新发布时应重新选择）
     */
    OrderReuseVO getReuseTemplate(Integer orderId);

    /**
     * 发布者提醒接单者（催单）
     * 权限：仅订单发布者本人；订单须处于已接单/取件中/配送中；
     * 10 分钟内限一次（防刷，避免频繁打扰接单者）
     */
    void urgeTaker(Integer orderId);

    /**
     * 取消订单（R-11 / R-12）
     * - 待接单：发布者无条件取消，原因必选发布者清单（CancelReason.PUBLISHER_REASONS）
     * - 已接单/取件中：接单者申请取消，原因必选接单者清单（CancelReason.TAKER_REASONS），扣信誉分 5，订单退回待接单
     * - 配送中及之后禁止取消
     */
    void cancelOrder(Integer orderId, String cancelReason);

    /**
     * 抢单（R-6 ~ R-10）：乐观锁 + 事务，并发安全
     * 禁止接自己的单 / 信誉分≥80 / 不超过等级对应进行中上限
     */
    void takeOrder(Integer orderId);

    /**
     * 状态流转（R-1 ~ R-5）：严格状态机，禁止跨跳；
     * 配送中 → 待收货（接单者确认送达）、待收货 → 已完成（R-3 仅需求方触发）；变更同步写历史表（R-4）
     * @param skipPhoto 流转到「待收货」时，若未上传送达照片，需显式 true 表示「无法拍照」才放行
     */
    void updateOrderStatus(Integer orderId, String targetStatus, Integer operatorId, String changeNote, boolean skipPhoto);

    /**
     * 接单者确认送达（配送中 → 待收货）
     * 权限：仅接单者本人；记录确认送达时间，需求方须在 confirmReceiptHours 内确认收货，超时自动确认
     * 送达照片默认要求上传，无法拍照时传 skipPhoto=true 跳过
     */
    void confirmArrival(Integer orderId, boolean skipPhoto);

    /**
     * 上传订单照片凭证（取件选填 / 送达必填）
     * 权限：仅订单接单者本人；type=pickup 允许在已接单/取件中上传，type=deliver 允许在配送中上传
     * @return 照片可访问相对路径（/uploads/order/xxx）
     */
    String uploadOrderPhoto(Integer orderId, String type, org.springframework.web.multipart.MultipartFile file);

    /**
     * 需求大厅分页（仅待接单，按发布时间降序）
     * @param keyword   关键词：模糊匹配快递公司/取件码/取件地点/送达地点/备注，空则不过滤
     * @param minReward 报酬下限，空则不过滤
     * @param maxReward 报酬上限，空则不过滤
     */
    IPage<OrderVO> getHallOrderPage(long current, long size, String keyword,
                                    BigDecimal minReward, BigDecimal maxReward);

    /** 我的发布分页（按状态分组：进行中/历史，3.7） */
    IPage<OrderVO> getMyPublishPage(long current, long size, List<String> statusList);

    /** 我的接单分页 */
    IPage<OrderVO> getMyTakePage(long current, long size, List<String> statusList);

    /** 订单详情：订单 + 双方昵称 + 完整状态流转 + 双向评价 */
    OrderDetailVO getOrderVOById(Integer orderId);
}
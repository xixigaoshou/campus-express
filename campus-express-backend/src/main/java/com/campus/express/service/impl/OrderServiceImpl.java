package com.campus.express.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.express.dto.OrderPublishDTO;
import com.campus.express.dto.OrderUpdateDTO;
import com.campus.express.service.OrderLifecycleService;
import com.campus.express.service.OrderRelayService;
import com.campus.express.service.OrderService;
import com.campus.express.service.OrderTimeoutService;
import com.campus.express.vo.OrderDetailVO;
import com.campus.express.vo.OrderReuseVO;
import com.campus.express.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单服务门面（Facade）
 *
 * <p>对外保持 {@link OrderService} 契约不变，内部把原先 1181 行的「上帝类」按职责拆分为三个领域服务：
 * <ul>
 *   <li>{@link OrderLifecycleService}：订单流转（发布/修改/抢单/状态机/取消/催单/照片/查询）</li>
 *   <li>{@link OrderRelayService}：转单接力（发起/撤回/承接/专区/接力超时撤回）</li>
 *   <li>{@link OrderTimeoutService}：超时调度（无人接单提醒/超时取消/超时自动确认收货）</li>
 * </ul>
 * 门面仅做委派，不再承载业务规则；Controller 与既有调用方无需任何改动。
 * 定时任务注解（{@code @Scheduled}）保留在领域服务实现上，本门面不得重复标注，避免任务被触发两次。</p>
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderLifecycleService lifecycleService;
    private final OrderRelayService relayService;
    private final OrderTimeoutService timeoutService;

    // ---------------- 订单流转 ----------------

    @Override
    public Integer publishOrder(OrderPublishDTO dto) {
        return lifecycleService.publishOrder(dto);
    }

    @Override
    public void updateWaitOrder(OrderUpdateDTO dto) {
        lifecycleService.updateWaitOrder(dto);
    }

    @Override
    public OrderReuseVO getReuseTemplate(Integer orderId) {
        return lifecycleService.getReuseTemplate(orderId);
    }

    @Override
    public void urgeTaker(Integer orderId) {
        lifecycleService.urgeTaker(orderId);
    }

    @Override
    public void cancelOrder(Integer orderId, String cancelReason) {
        lifecycleService.cancelOrder(orderId, cancelReason);
    }

    @Override
    public void takeOrder(Integer orderId) {
        lifecycleService.takeOrder(orderId);
    }

    @Override
    public void updateOrderStatus(Integer orderId, String targetStatus, Integer operatorId, String changeNote, boolean skipPhoto) {
        lifecycleService.updateOrderStatus(orderId, targetStatus, operatorId, changeNote, skipPhoto);
    }

    @Override
    public void confirmArrival(Integer orderId, boolean skipPhoto) {
        lifecycleService.confirmArrival(orderId, skipPhoto);
    }

    @Override
    public String uploadOrderPhoto(Integer orderId, String type, MultipartFile file) {
        return lifecycleService.uploadOrderPhoto(orderId, type, file);
    }

    @Override
    public IPage<OrderVO> getHallOrderPage(long current, long size, String keyword,
                                           BigDecimal minReward, BigDecimal maxReward) {
        return lifecycleService.getHallOrderPage(current, size, keyword, minReward, maxReward);
    }

    @Override
    public IPage<OrderVO> getMyPublishPage(long current, long size, List<String> statusList) {
        return lifecycleService.getMyPublishPage(current, size, statusList);
    }

    @Override
    public IPage<OrderVO> getMyTakePage(long current, long size, List<String> statusList) {
        return lifecycleService.getMyTakePage(current, size, statusList);
    }

    @Override
    public OrderDetailVO getOrderVOById(Integer orderId) {
        return lifecycleService.getOrderVOById(orderId);
    }

    // ---------------- 转单接力 ----------------

    @Override
    public void initiateTransfer(Integer orderId, String reason) {
        relayService.initiateTransfer(orderId, reason);
    }

    @Override
    public void cancelTransfer(Integer orderId) {
        relayService.cancelTransfer(orderId);
    }

    @Override
    public void relayTakeOrder(Integer orderId) {
        relayService.relayTakeOrder(orderId);
    }

    @Override
    public IPage<OrderVO> getRelayOrderPage(long current, long size, String keyword,
                                            BigDecimal minReward, BigDecimal maxReward) {
        return relayService.getRelayOrderPage(current, size, keyword, minReward, maxReward);
    }

    // ---------------- 超时调度（定时任务入口，注解在领域服务实现上） ----------------

    /** 转单接力超时自动撤回（定时任务入口，委派 {@link OrderRelayService}） */
    public void relayTimeoutTask() {
        relayService.relayTimeoutTask();
    }

    /** 超时提醒与自动取消（定时任务入口，委派 {@link OrderTimeoutService}） */
    public void timeoutRemindCancelTask() {
        timeoutService.timeoutRemindCancelTask();
    }

    /** 待收货超时自动确认收货（定时任务入口，委派 {@link OrderTimeoutService}） */
    public void autoConfirmReceiptTask() {
        timeoutService.autoConfirmReceiptTask();
    }
}
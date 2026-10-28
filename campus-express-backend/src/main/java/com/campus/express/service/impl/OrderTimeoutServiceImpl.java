package com.campus.express.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.express.common.BusinessException;
import com.campus.express.common.OrderStatus;
import com.campus.express.common.TransferStatus;
import com.campus.express.config.BusinessRulesConfig;
import com.campus.express.entity.Order;
import com.campus.express.mapper.OrderMapper;
import com.campus.express.service.NotificationService;
import com.campus.express.service.OrderRelayService;
import com.campus.express.service.OrderStatusHistoryService;
import com.campus.express.service.OrderTimeoutService;
import com.campus.express.service.UserService;
import com.campus.express.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时调度领域服务实现
 *
 * <p>承载订单超时的全部批量扫描与自动处理：过期未接单自动取消、无人接单提醒、
 * 超时未取件退回待接单、超时未送达终态取消、待收货超时自动确认收货。</p>
 *
 * <p>批量任务不加 {@code @Transactional}：批量事务会让单笔失败回滚整批、并永久卡住后续订单；
 * 改为每单在独立事务（REQUIRES_NEW）中处理，单笔异常仅记录日志并跳过。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTimeoutServiceImpl implements OrderTimeoutService {

    private final OrderMapper orderMapper;
    private final UserService userService;
    private final OrderStatusHistoryService historyService;
    private final NotificationService notificationService;
    private final WalletService walletService;
    private final BusinessRulesConfig rules;
    private final PlatformTransactionManager transactionManager;
    private final OrderRelayService relayService;

    /**
     * 定时任务：待收货超时自动确认收货（每 60 秒扫描一次，@EnableScheduling 启用）
     * 规则：接单者确认送达后 confirmReceiptHours（默认 24 小时）内需求方未确认 → 系统自动确认收货，报酬结算给接单者
     * 说明：不加 @Transactional，每单在独立事务中处理，单笔结算异常不影响其余订单；
     * 结算失败的单整单回滚（保持「待收货」），下一轮扫描自动重试，不会出现「已完成但未结算」
     */
    @Override
    @Scheduled(fixedDelay = 60_000)
    public void autoConfirmReceiptTask() {
        LocalDateTime now = LocalDateTime.now();
        List<Order> pendingOrders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.WAIT_CONFIRM)
                .isNotNull(Order::getConfirmArrivalTime)
                .lt(Order::getConfirmArrivalTime, now.minusHours(rules.getTimeout().getConfirmReceiptHours())));
        for (Order o : pendingOrders) {
            runPerOrderTx(o.getOrderId(), "自动确认收货", () -> autoConfirmOneOrder(o));
        }
        if (!pendingOrders.isEmpty()) {
            log.info("[自动确认收货] 超时自动完成 {} 单", pendingOrders.size());
        }
    }

    /**
     * 单笔自动确认收货：状态流转 + 写历史 + 结算 + 通知
     * 按单独立事务执行，某单异常不会回滚其他已完成的订单、也不中断后续订单
     */
    private void autoConfirmOneOrder(Order o) {
        o.setStatus(OrderStatus.COMPLETED);
        // 乐观锁 version 条件：发布者若已抢先确认收货，此处影响行数为 0，
        // 跳过本单，避免与发布者侧重复结算（重复扣款/重复入账）
        if (orderMapper.updateById(o) == 0) {
            return;
        }
        historyService.saveHistoryRecord(OrderDomainSupport.buildHistory(o.getOrderId(), OrderStatus.WAIT_CONFIRM,
                OrderStatus.COMPLETED, o.getPublisherId(), "需求方超时未确认收货，系统自动确认（"
                + rules.getTimeout().getConfirmReceiptHours() + "小时）"));
        // R-28：结算与订单完成必须「同成同败」。
        // 若吞掉结算异常继续提交，会出现「订单已完成、报酬既没扣冻结也没入账」的账实不符：
        // 接单者拿不到钱、发布者冻结资金永久卡住，而且订单已进入终态，再也没有任何路径会重试。
        // 这里让异常向上抛出，由 runPerOrderTx 的 REQUIRES_NEW 事务整体回滚（状态/历史一并回滚），
        // 订单保持「待收货」，下一轮扫描自动重试，直到结算成功为止。
        try {
            walletService.settleOrderReward(o.getPublisherId(), o.getTakerId(), o.getOrderId(), o.getRewardAmount());
        } catch (BusinessException e) {
            // 内存对象同步回滚，避免调用方误以为本单已完成
            o.setStatus(OrderStatus.WAIT_CONFIRM);
            log.error("[自动确认收货] 订单#{} 结算失败，已回滚本次自动确认，订单保持待收货等待下一轮重试：{}",
                    o.getOrderId(), e.getMessage());
            throw e;
        }
        notificationService.send(o.getPublisherId(), o.getOrderId(), "订单已自动确认收货，报酬已结算给接单者，请及时评价");
        notificationService.send(o.getTakerId(), o.getOrderId(), "订单已自动确认收货，报酬已到账，请及时评价");
    }

    /**
     * 待接单且期望取件时间已过：自动取消并解冻发布者报酬
     * 现实依据：期望取件时间已过的需求，大厅不再展示（selectHallPage 过滤 expect_time > NOW()）、
     * 也无法被接单（takeOrder 拒绝过期单），若不自动取消，订单将永久滞留「待接单」，
     * 发布者冻结的报酬被永久占用无法退回（与「降低报酬后冻结差额卡死」同类的资金泄漏）
     * 并发安全：更新条件带 status='待接单'，与抢单乐观锁互斥，已被抢走的订单影响行数为 0 自动跳过
     *
     * @param now 当前时间
     * @return 本次自动取消的订单数
     */
    private int cancelExpiredWaitOrders(LocalDateTime now) {
        List<Order> expired = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.WAIT_ACCEPT)
                .isNotNull(Order::getExpectTime)
                .lt(Order::getExpectTime, now));
        int cancelled = 0;
        for (Order o : expired) {
            int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getOrderId, o.getOrderId())
                    .eq(Order::getStatus, OrderStatus.WAIT_ACCEPT)
                    .set(Order::getStatus, OrderStatus.CANCELLED)
                    .set(Order::getCancelReason, "期望取件时间已过，自动取消")
                    .set(Order::getUpdateTime, LocalDateTime.now())
                    .set(Order::getVersion, o.getVersion() + 1));
            if (rows == 0) {
                continue;
            }
            historyService.saveHistoryRecord(OrderDomainSupport.buildHistory(o.getOrderId(), OrderStatus.WAIT_ACCEPT,
                    OrderStatus.CANCELLED, o.getPublisherId(),
                    "期望取件时间已过仍无人接单，系统自动取消并解冻报酬"));
            try {
                walletService.unfreezeBalance(o.getPublisherId(), o.getOrderId(), o.getRewardAmount());
            } catch (BusinessException e) {
                log.warn("[过期未接单] 订单#{} 解冻失败（{}），请人工核查", o.getOrderId(), e.getMessage());
            }
            notificationService.send(o.getPublisherId(), o.getOrderId(),
                    "您的订单#" + o.getOrderId() + " 期望取件时间已过仍无人接单，已自动取消，冻结报酬已退回钱包");
            cancelled++;
        }
        return cancelled;
    }

    /**
     * 在独立事务中执行单笔订单的定时处理（REQUIRES_NEW）
     * 目的：某单失败只回滚该单，不会因整批共用事务而回滚已处理成功的订单，
     * 也不会中断后续订单——否则某单反复失败会让整批任务永久卡死
     */
    private void runPerOrderTx(Integer orderId, String scene, Runnable action) {
        OrderDomainSupport.runPerOrderTx(transactionManager, orderId, scene, action);
    }

    /**
     * 定时任务：超时提醒与自动取消（每 60 秒扫描一次，@EnableScheduling 启用）
     * ⓪待接单但期望取件时间已过 → 自动取消并解冻报酬（否则永久滞留、冻结资金无法退回）
     * ①发布 24h 无人接单 → 提醒发布者调整（R-14，幂等：同订单只提醒一次，避免每轮扫描重复刷屏）
     * ②已接单但超过期望取件时间仍未取件 → 自动取消并扣信誉分，退回待接单（R-15，原状态守卫 + 按单独立事务）
     * ③取件中/配送中超过期望取件时间仍未送达 → 自动取消（终态）、扣信誉分、解冻发布者报酬
     * 说明：本方法不加 @Transactional——批量事务会让单笔失败回滚整批、并永久卡住后续订单；
     * 改为每单在独立事务中处理，单笔异常仅记录日志并跳过
     */
    @Override
    @Scheduled(fixedDelay = 60_000)
    public void timeoutRemindCancelTask() {
        LocalDateTime now = LocalDateTime.now();
        // ⓪ 待接单但期望取件时间已过：自动取消并解冻报酬，避免订单永久滞留、冻结资金无法退回
        int expiredCancelled = cancelExpiredWaitOrders(now);
        // ① 待接单超时提醒（R-14）：只提醒，不自动取消；已提醒过该订单则跳过
        List<Order> waitOrders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.WAIT_ACCEPT)
                .lt(Order::getCreateTime, now.minusHours(rules.getTimeout().getWaitAcceptHours())));
        int remindCount = 0;
        for (Order o : waitOrders) {
            if (notificationService.hasNoTakerReminder(o.getOrderId())) {
                continue;
            }
            notificationService.send(o.getPublisherId(), o.getOrderId(),
                    "您的订单发布已超 " + rules.getTimeout().getWaitAcceptHours() + " 小时仍无人接单，可调整报酬或延长时限");
            remindCount++;
        }

        // ② 已接单但超过期望取件时间仍未取件（R-15）：自动取消 + 扣信誉分 + 退回待接单
        List<Order> stuckOrders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.ACCEPTED)
                .isNotNull(Order::getExpectTime)
                .lt(Order::getExpectTime, now.minusMinutes(rules.getTimeout().getPickupOverdueMinutes())));
        for (Order o : stuckOrders) {
            runPerOrderTx(o.getOrderId(), "超时提醒/取消-未取件", () -> handleStuckAcceptedOrder(o));
        }

        // ③ 取件中/配送中长时间未送达（R-15 扩展）：超过「取件时间」pickingOverdueHours 小时仍未送达
        // 包裹已在接单者手中，无法退回待接单，故订单置为终态「已取消」、扣接单者信誉分、解冻发布者报酬
        // 计时基准：优先取 pickup_time（已接单 → 取件中时记录）；历史订单无该字段时退回期望取件时间。
        // 不能只用期望取件时间：用户晚于期望时间取件时，取件后很快（甚至立刻）就会撞上
        // 「期望时间 + N 小时」而被误判超时取消
        LocalDateTime deliverDeadline = now.minusHours(rules.getTimeout().getPickingOverdueHours());
        List<Order> overdueDeliver = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .in(Order::getStatus, List.of(OrderStatus.PICKING, OrderStatus.DELIVERING))
                .and(w -> w.lt(Order::getPickupTime, deliverDeadline)
                        .or(x -> x.isNull(Order::getPickupTime)
                                .isNotNull(Order::getExpectTime)
                                .lt(Order::getExpectTime, deliverDeadline))));
        for (Order o : overdueDeliver) {
            // 内存兜底：防止查询与处理之间订单刚被接单者推进（取件时间被刷新）而误取消
            if (o.getPickupTime() != null && !isDeliverOverdue(o, now)) {
                continue;
            }
            runPerOrderTx(o.getOrderId(), "超时提醒/取消-未送达", () -> handleOverdueDeliveryOrder(o));
        }

        if (remindCount > 0 || !stuckOrders.isEmpty() || !overdueDeliver.isEmpty() || expiredCancelled > 0) {
            log.info("[超时提醒/取消] 过期未接单自动取消{}单 / 提醒{}单 / 未取件自动取消{}单 / 超时未送达自动取消{}单",
                    expiredCancelled, remindCount, stuckOrders.size(), overdueDeliver.size());
        }
    }

    /**
     * 任务②单笔处理：已接单超时未取件 → 退回待接单 + 扣信誉分 + 通知双方
     * 原状态守卫：接单者若已把订单推进为「取件中」，此处影响行数为 0，直接跳过该单，
     * 避免把已推进的订单错误打回「待接单」并误扣信誉分（改由任务③按正确路径处理）
     */
    private void handleStuckAcceptedOrder(Order o) {
        // 先锁定接单者用户行，统一「用户 → 订单 → 钱包」加锁顺序，
        // 避免与抢单/接力承接的加锁顺序相反造成死锁
        userService.lockForUpdate(o.getTakerId());
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getOrderId, o.getOrderId())
                .eq(Order::getStatus, OrderStatus.ACCEPTED)
                .set(Order::getStatus, OrderStatus.WAIT_ACCEPT)
                .set(Order::getTakerId, null)
                .set(Order::getCancelReason, "超过期望取件时间未取件，自动取消")
                .set(Order::getTransferStatus, null)
                .set(Order::getTransferReason, null)
                // 退回待接单后由下一位接单者重新取证：清空上一任的凭证，避免照片错配
                .set(Order::getPickupPhoto, null)
                .set(Order::getDeliverPhoto, null)
                .set(Order::getUpdateTime, LocalDateTime.now())
                .set(Order::getVersion, o.getVersion() + 1));
        if (rows == 0) {
            return;
        }
        // 状态流转成功后再扣分：保证不会出现「分已扣、订单却未被处理」的不可回滚状态
        userService.updateCreditScore(o.getTakerId(), -rules.getCredit().getBreachDeduct(),
                "接单超时",
                "超过期望取件时间仍未取件，自动取消（订单#" + o.getOrderId() + "），违约扣信誉分5",
                o.getOrderId());
        relayService.closeActiveTransfer(o.getOrderId(), TransferStatus.EXPIRED);
        historyService.saveHistoryRecord(OrderDomainSupport.buildHistory(o.getOrderId(), OrderStatus.ACCEPTED,
                OrderStatus.WAIT_ACCEPT, o.getTakerId(), "已接单超过期望取件时间仍未取件，自动取消并扣信誉分5（R-15）"));
        notificationService.send(o.getPublisherId(), o.getOrderId(),
                OrderDomainSupport.reopenNotice(o, "接单者超过期望取件时间仍未取件，订单已自动退回待接单"));
        // 接单者同步收到通知：订单从其列表消失却无任何提示会引发困惑；接力中的订单还需说明接力已失效
        notificationService.send(o.getTakerId(), o.getOrderId(),
                "订单#" + o.getOrderId() + " 因超过期望取件时间未取件，已被系统退回待接单，已扣信誉分5"
                        + (TransferStatus.PENDING.equals(o.getTransferStatus()) ? "；您发起的转单接力已随之失效" : ""));
    }

    /**
     * 判断取件后是否已超过送达时限：以取件时间为准，缺失时退回期望取件时间（兼容历史订单）
     */
    private boolean isDeliverOverdue(Order o, LocalDateTime now) {
        LocalDateTime base = o.getPickupTime() != null ? o.getPickupTime() : o.getExpectTime();
        return base != null && base.isBefore(now.minusHours(rules.getTimeout().getPickingOverdueHours()));
    }

    /**
     * 任务③单笔处理：取件中/配送中超时未送达 → 终态取消 + 扣信誉分 + 解冻发布者报酬 + 通知双方
     * 原状态守卫：订单可能已被接单者推进或取消，影响行数为 0 时跳过，避免重复扣分与错误的终态覆盖
     */
    private void handleOverdueDeliveryOrder(Order o) {
        // 先锁定接单者用户行，统一「用户 → 订单 → 钱包」加锁顺序，
        // 避免与抢单/接力承接的加锁顺序相反造成死锁
        userService.lockForUpdate(o.getTakerId());
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getOrderId, o.getOrderId())
                .eq(Order::getStatus, o.getStatus())
                .set(Order::getStatus, OrderStatus.CANCELLED)
                .set(Order::getCancelReason, "取件后超时未送达，自动取消")
                .set(Order::getTransferStatus, null)
                .set(Order::getTransferReason, null)
                .set(Order::getUpdateTime, LocalDateTime.now())
                .set(Order::getVersion, o.getVersion() + 1));
        if (rows == 0) {
            return;
        }
        // 状态流转成功后再扣分：保证不会出现「分已扣、订单却未被处理」的不可回滚状态
        userService.updateCreditScore(o.getTakerId(), -rules.getCredit().getBreachDeduct(),
                "配送超时",
                "取件后超过约定时间仍未送达，自动取消（订单#" + o.getOrderId() + "），违约扣信誉分5",
                o.getOrderId());
        relayService.closeActiveTransfer(o.getOrderId(), TransferStatus.EXPIRED);
        historyService.saveHistoryRecord(OrderDomainSupport.buildHistory(o.getOrderId(), o.getStatus(),
                OrderStatus.CANCELLED, o.getTakerId(),
                "取件后超过约定时间仍未送达，自动取消并扣信誉分5"));
        // 订单终态取消：解冻发布者报酬（冻结资金退回可用余额），异常不阻塞其余订单处理
        try {
            walletService.unfreezeBalance(o.getPublisherId(), o.getOrderId(), o.getRewardAmount());
        } catch (BusinessException e) {
            log.warn("[配送超时] 订单#{} 解冻失败（{}），请人工核查", o.getOrderId(), e.getMessage());
        }
        notificationService.send(o.getPublisherId(), o.getOrderId(),
                "接单者取件后超过 " + rules.getTimeout().getPickingOverdueHours()
                        + " 小时仍未送达，订单已自动取消，冻结报酬已退回");
        notificationService.send(o.getTakerId(), o.getOrderId(),
                "订单#" + o.getOrderId() + " 因取件后超时未送达被系统自动取消，已扣信誉分5"
                        + (TransferStatus.PENDING.equals(o.getTransferStatus()) ? "；您发起的转单接力已随之失效" : ""));
    }
}
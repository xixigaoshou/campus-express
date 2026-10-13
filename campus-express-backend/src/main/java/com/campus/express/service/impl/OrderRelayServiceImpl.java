package com.campus.express.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.express.common.BusinessException;
import com.campus.express.common.OrderStatus;
import com.campus.express.common.TransferReason;
import com.campus.express.common.TransferStatus;
import com.campus.express.config.BusinessRulesConfig;
import com.campus.express.entity.Order;
import com.campus.express.entity.OrderTransfer;
import com.campus.express.entity.User;
import com.campus.express.mapper.OrderMapper;
import com.campus.express.mapper.OrderTransferMapper;
import com.campus.express.service.NotificationService;
import com.campus.express.service.OrderRelayService;
import com.campus.express.service.OrderStatusHistoryService;
import com.campus.express.service.UserService;
import com.campus.express.util.CreditLevelUtil;
import com.campus.express.vo.OrderVO;
import com.campus.express.ws.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 转单接力领域服务实现
 *
 * <p>接单者取件前无法继续履约时，将订单挂到接力专区等待其他接单者承接。
 * 接力期间订单状态保持不变，仅以 {@code transfer_status} 标记挂单，原接单者不扣信誉分；
 * 并发承接采用乐观锁（version + transfer_status + taker_id 条件更新）保证「一单一人」。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderRelayServiceImpl implements OrderRelayService {

    private final OrderMapper orderMapper;
    private final OrderTransferMapper transferMapper;
    private final UserService userService;
    private final OrderStatusHistoryService historyService;
    private final NotificationService notificationService;
    private final WebSocketSessionManager webSocketSessionManager;
    private final BusinessRulesConfig rules;

    /**
     * 发起转单接力：接单者取件前无法继续履约时，把订单挂到接力专区等待其他接单者接力
     * 不改变订单状态（仍为已接单/取件中）、不扣信誉分；同一订单同时仅允许一个接力挂单
     */
    @Override
    @Transactional
    public void initiateTransfer(Integer orderId, String reason) {
        User current = userService.getCurrentUser();
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // 权限：仅接单者本人
        if (!current.getUserId().equals(order.getTakerId())) {
            throw new BusinessException(403, "仅接单者可发起转单接力");
        }
        // 转单权限：信誉分门槛
        int minCredit = rules.getRelay().getMinCredit();
        if (current.getCreditScore() < minCredit) {
            throw new BusinessException("信誉分低于 " + minCredit + "，暂无转单接力权限，请先提升信誉分");
        }
        // 转单权限：每周次数上限（自然周，周一 00:00 重置；含已撤回/已过期的发起记录）
        int weeklyLimit = rules.getRelay().getWeeklyLimit();
        Long weeklyCount = transferMapper.selectCount(new LambdaQueryWrapper<OrderTransfer>()
                .eq(OrderTransfer::getFromTakerId, current.getUserId())
                .ge(OrderTransfer::getCreateTime, weekStart()));
        if (weeklyCount >= weeklyLimit) {
            throw new BusinessException("本周转单接力次数已达上限 " + weeklyLimit + " 次，请下周再试");
        }
        // 状态：仅取件前（已接单/取件中）可转单；配送中包裹已在手，不可转
        if (!OrderStatus.ACCEPTED.equals(order.getStatus()) && !OrderStatus.PICKING.equals(order.getStatus())) {
            throw new BusinessException("当前状态（" + order.getStatus() + "）不可发起转单接力");
        }
        if (TransferStatus.PENDING.equals(order.getTransferStatus())) {
            throw new BusinessException("订单已在转单接力中，请勿重复发起");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("请选择转单原因");
        }
        if (!TransferReason.isValid(reason)) {
            throw new BusinessException("请选择有效的转单原因");
        }
        // 挂起接力：订单状态不变，仅打上接力标记等待他人承接
        orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getOrderId, orderId)
                .set(Order::getTransferStatus, TransferStatus.PENDING)
                .set(Order::getTransferReason, reason)
                .set(Order::getUpdateTime, LocalDateTime.now()));
        // 写入转单流水，供追溯与并发控制
        OrderTransfer t = new OrderTransfer();
        t.setOrderId(orderId);
        t.setFromTakerId(current.getUserId());
        t.setReason(reason);
        t.setStatus(TransferStatus.PENDING);
        t.setCreateTime(LocalDateTime.now());
        transferMapper.insert(t);
        // 状态未变，但转单属关键业务动作，写轨迹便于双方在时间线中追溯
        historyService.saveHistoryRecord(OrderDomainSupport.buildHistory(orderId, order.getStatus(), order.getStatus(),
                current.getUserId(), "发起转单接力（" + reason + "），等待其他接单者接力"));
        notificationService.send(order.getPublisherId(), orderId,
                "订单#" + orderId + " 接单者发起转单接力（" + reason + "），正在等待其他接单者接力");
        webSocketSessionManager.broadcast("new_relay");
    }

    /** 撤回转单接力：仅原接单者本人；撤回后订单继续由本人履约 */
    @Override
    @Transactional
    public void cancelTransfer(Integer orderId) {
        User current = userService.getCurrentUser();
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!current.getUserId().equals(order.getTakerId())) {
            throw new BusinessException(403, "仅接单者可撤回转单接力");
        }
        if (!TransferStatus.PENDING.equals(order.getTransferStatus())) {
            throw new BusinessException("订单当前未处于转单接力中");
        }
        orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getOrderId, orderId)
                .set(Order::getTransferStatus, null)
                .set(Order::getTransferReason, null)
                .set(Order::getUpdateTime, LocalDateTime.now()));
        closeActiveTransfer(orderId, TransferStatus.WITHDRAWN);
        historyService.saveHistoryRecord(OrderDomainSupport.buildHistory(orderId, order.getStatus(), order.getStatus(),
                current.getUserId(), "撤回转单接力，继续由本人履约"));
        notificationService.send(order.getPublisherId(), orderId,
                "订单#" + orderId + " 接单者已撤回转单接力，订单继续由其履约");
        webSocketSessionManager.broadcast("new_relay");
    }

    /**
     * 接力接单（乐观锁 + 事务）：其他接单者承接接力订单，接单者由原接单者换为接力者
     * 订单状态保持不变（已接单/取件中），原接单者不扣信誉分
     */
    @Override
    @Transactional
    public void relayTakeOrder(Integer orderId) {
        User taker = userService.getCurrentUser();
        // 角色权限：仅接单者（canPublish=false）可接力
        if (Boolean.TRUE.equals(taker.getCanPublish())) {
            throw new BusinessException(403, "当前账号为发布者角色，不可接力接单");
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!TransferStatus.PENDING.equals(order.getTransferStatus())) {
            throw new BusinessException("该订单当前不在转单接力中");
        }
        // 状态守卫：仅取件前（已接单/取件中）可接力；配送中包裹已在手、终态订单均不可接力
        if (!OrderStatus.ACCEPTED.equals(order.getStatus()) && !OrderStatus.PICKING.equals(order.getStatus())) {
            throw new BusinessException("当前状态（" + order.getStatus() + "）不可接力承接");
        }
        // 时间守卫：期望取件时间已过的订单不再开放接力（与接力专区列表口径一致）
        if (order.getExpectTime() != null && !order.getExpectTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException("该订单期望取件时间已过，不可接力承接");
        }
        // 不能接力自己发起的转单，也不能接力自己发布的订单
        if (taker.getUserId().equals(order.getTakerId())) {
            throw new BusinessException("不能接力自己发起的转单");
        }
        if (taker.getUserId().equals(order.getPublisherId())) {
            throw new BusinessException("不能接力自己发布的订单");
        }
        // 信誉分门槛（R-23）
        if (taker.getCreditScore() < rules.getCredit().getBanThreshold()) {
            throw new BusinessException("信誉分低于 " + rules.getCredit().getBanThreshold()
                    + "，暂不可接单，请先提升信誉分");
        }
        // 同时进行中订单上限（R-9）
        Long activeCount = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getTakerId, taker.getUserId())
                .in(Order::getStatus, List.of(OrderStatus.ACTIVE)));
        int limit = CreditLevelUtil.concurrentLimit(taker.getCreditScore(), rules);
        if (activeCount >= limit) {
            throw new BusinessException("当前信誉等级（" + CreditLevelUtil.levelOf(taker.getCreditScore(), rules)
                    + "）同时进行中订单已达上限 " + limit + " 单，无法接力");
        }
        Integer oldTakerId = order.getTakerId();
        // 乐观锁：条件含 transfer_status/taker_id/version，影响行数 0 表示已被他人接力
        int rows = orderMapper.relayTakeOrder(orderId, taker.getUserId(), oldTakerId, order.getVersion());
        if (rows == 0) {
            throw new BusinessException("手慢了，该订单已被他人接力承接或已不满足接力条件");
        }
        // 关闭接力流水并登记接力者
        transferMapper.update(null, new LambdaUpdateWrapper<OrderTransfer>()
                .eq(OrderTransfer::getOrderId, orderId)
                .eq(OrderTransfer::getStatus, TransferStatus.PENDING)
                .set(OrderTransfer::getStatus, TransferStatus.DONE)
                .set(OrderTransfer::getToTakerId, taker.getUserId())
                .set(OrderTransfer::getFinishTime, LocalDateTime.now()));
        // 状态未变，写轨迹记录接力换人（带出双方昵称便于追溯）
        User fromUser = userService.getById(oldTakerId);
        String fromName = fromUser == null ? ("用户#" + oldTakerId) : fromUser.getUsername();
        historyService.saveHistoryRecord(OrderDomainSupport.buildHistory(orderId, order.getStatus(), order.getStatus(),
                taker.getUserId(), "转单接力：" + fromName + " → " + taker.getUsername() + "，订单由接力者继续履约"));
        notificationService.send(oldTakerId, orderId,
                "您的转单已被 " + taker.getUsername() + " 接力承接，订单由其继续履约");
        notificationService.send(order.getPublisherId(), orderId,
                "订单#" + orderId + " 已由 " + taker.getUsername() + " 接力承接，订单继续履约");
        webSocketSessionManager.broadcast("new_relay");
    }

    /** 接力专区分页：transfer_status='待接力' 的订单（支持关键词/报酬范围筛选） */
    @Override
    public IPage<OrderVO> getRelayOrderPage(long current, long size, String keyword,
                                            BigDecimal minReward, BigDecimal maxReward) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return orderMapper.selectRelayPage(new Page<>(current, size), kw, minReward, maxReward);
    }

    /** 关闭订单当前挂起的转单接力流水（接力成功/撤回/过期时调用，仅作用于「待接力」记录） */
    @Override
    public void closeActiveTransfer(Integer orderId, String status) {
        transferMapper.update(null, new LambdaUpdateWrapper<OrderTransfer>()
                .eq(OrderTransfer::getOrderId, orderId)
                .eq(OrderTransfer::getStatus, TransferStatus.PENDING)
                .set(OrderTransfer::getStatus, status)
                .set(OrderTransfer::getFinishTime, LocalDateTime.now()));
    }

    /**
     * 定时任务：转单接力超时自动撤回（每 60 秒扫描一次）
     * 规则：发起接力后 relay.timeoutMinutes（默认 30 分钟）内无人接力 → 自动撤回接力标记，通知原接单者继续履约
     */
    @Override
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void relayTimeoutTask() {
        LocalDateTime now = LocalDateTime.now();
        List<Order> pending = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getTransferStatus, TransferStatus.PENDING)
                .lt(Order::getUpdateTime, now.minusMinutes(rules.getRelay().getTimeoutMinutes())));
        for (Order o : pending) {
            orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getOrderId, o.getOrderId())
                    .eq(Order::getTransferStatus, TransferStatus.PENDING)
                    .set(Order::getTransferStatus, null)
                    .set(Order::getTransferReason, null)
                    .set(Order::getUpdateTime, LocalDateTime.now()));
            closeActiveTransfer(o.getOrderId(), TransferStatus.EXPIRED);
            historyService.saveHistoryRecord(OrderDomainSupport.buildHistory(o.getOrderId(), o.getStatus(), o.getStatus(),
                    o.getTakerId(), "转单接力超时无人接力，自动撤回（"
                    + rules.getRelay().getTimeoutMinutes() + "分钟）"));
            notificationService.send(o.getTakerId(), o.getOrderId(),
                    "您的转单接力已超时无人接力，已自动撤回，请继续履约或取消订单");
            notificationService.send(o.getPublisherId(), o.getOrderId(),
                    "订单#" + o.getOrderId() + " 转单接力超时已自动撤回，原接单者将继续履约");
            webSocketSessionManager.broadcast("new_relay");
        }
        if (!pending.isEmpty()) {
            log.info("[转单接力] 超时自动撤回 {} 单", pending.size());
        }
    }

    /** 本周一 00:00（自然周起点，用于统计本周转单接力次数） */
    private LocalDateTime weekStart() {
        return LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
    }
}
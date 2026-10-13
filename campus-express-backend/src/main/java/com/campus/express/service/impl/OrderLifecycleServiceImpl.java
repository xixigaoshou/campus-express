package com.campus.express.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.express.common.BusinessException;
import com.campus.express.common.CancelReason;
import com.campus.express.common.OrderStatus;
import com.campus.express.common.TransferStatus;
import com.campus.express.config.BusinessRulesConfig;
import com.campus.express.dto.OrderPublishDTO;
import com.campus.express.dto.OrderUpdateDTO;
import com.campus.express.entity.Evaluation;
import com.campus.express.entity.Order;
import com.campus.express.entity.User;
import com.campus.express.mapper.EvaluationMapper;
import com.campus.express.mapper.OrderMapper;
import com.campus.express.service.NotificationService;
import com.campus.express.service.OrderLifecycleService;
import com.campus.express.service.OrderStatusHistoryService;
import com.campus.express.service.UserService;
import com.campus.express.service.WalletService;
import com.campus.express.util.CreditLevelUtil;
import com.campus.express.util.ImageUtil;
import com.campus.express.vo.OrderDetailVO;
import com.campus.express.vo.OrderReuseVO;
import com.campus.express.vo.OrderVO;
import com.campus.express.ws.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 订单流转领域服务实现：系统业务核心
 * 覆盖需求发布/修改、抢单、履约状态机流转、取消、催单、照片凭证与订单查询（业务规则 R-1 ~ R-20）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderLifecycleServiceImpl implements OrderLifecycleService {

    private final OrderMapper orderMapper;
    private final EvaluationMapper evaluationMapper;
    private final UserService userService;
    private final OrderStatusHistoryService historyService;
    private final NotificationService notificationService;
    private final WalletService walletService;
    private final WebSocketSessionManager webSocketSessionManager;
    private final BusinessRulesConfig rules;

    /**
     * 状态机流转映射（规则 R-1）
     * 正向：待接单→已接单→取件中→配送中→待收货→已完成；终态：已完成/已取消
     * 配送中→待收货：接单者「确认送达」；待收货→已完成：需求方「确认收货」或 24 小时超时自动确认
     * 取消走 cancelOrder 独立方法（R-2/R-12）
     */
    private static final Map<String, List<String>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.WAIT_ACCEPT, List.of(OrderStatus.ACCEPTED),
            OrderStatus.ACCEPTED, List.of(OrderStatus.PICKING),
            OrderStatus.PICKING, List.of(OrderStatus.DELIVERING),
            OrderStatus.DELIVERING, List.of(OrderStatus.WAIT_CONFIRM),
            OrderStatus.WAIT_CONFIRM, List.of(OrderStatus.COMPLETED)
    );

    /** 发布代取需求（3.3）：校验报酬范围（R-17），初始化 version=0，写首条历史 */
    @Override
    @Transactional
    public Integer publishOrder(OrderPublishDTO dto) {
        // R-17：报酬金额 2 ~ 50 元
        validateRewardRange(dto.getRewardAmount());
        // 期望取件时间必须晚于当前时间
        validateExpectTime(dto.getExpectTime());
        // 取件码唯一：同一「取件码 + 取件地点」不能同时存在于多个进行中订单（历史订单释放后可再次使用）
        validatePickupCodeUnique(dto.getPickupCode(), dto.getPickupLocation(), null);
        User current = userService.getCurrentUser();
        // 角色权限：仅发布者（canPublish=true）可发布需求（页面与接口双重校验）
        if (!Boolean.TRUE.equals(current.getCanPublish())) {
            throw new BusinessException(403, "当前账号为接单者角色，无发布需求权限");
        }
        // 发布订单将冻结报酬（付钱），须校验支付密码；未设置会引导先到个人中心设置
        userService.verifyPayPassword(current.getUserId(), dto.getPayPassword());
        // 发布者取消频控（反刷单）：当日主动取消待接单订单达上限后，当日禁止再发布
        int dailyCancelLimit = rules.getCancel().getDailyLimit();
        long todayCancels = historyService.countPublisherCancelSince(
                current.getUserId(), LocalDate.now().atStartOfDay());
        if (todayCancels >= dailyCancelLimit) {
            throw new BusinessException("今日已取消 " + todayCancels + " 个订单，达到上限 "
                    + dailyCancelLimit + " 次，今日暂不可再发布，请明日再试");
        }

        Order order = new Order();
        order.setPublisherId(current.getUserId());
        order.setTakerId(null);
        order.setPickupCode(dto.getPickupCode());
        order.setExpressCompany(dto.getExpressCompany());
        order.setPickupLocation(dto.getPickupLocation());
        order.setDeliverLocation(dto.getDeliverLocation());
        order.setExpectTime(dto.getExpectTime());
        order.setRemark(dto.getRemark());
        order.setRewardAmount(dto.getRewardAmount());
        order.setVersion(0);            // 乐观锁版本号初始为 0（R-7）
        order.setStatus(OrderStatus.WAIT_ACCEPT);
        orderMapper.insert(order);
        // R-25/R-26：订单入库后冻结报酬并关联订单号（余额不足抛异常，事务回滚订单创建；资金与订单共用同一事务）
        walletService.freezeBalance(current.getUserId(), order.getOrderId(), dto.getRewardAmount());

        // 写入首条状态历史（R-4）
        historyService.saveHistoryRecord(OrderDomainSupport.buildHistory(order.getOrderId(), null,
                OrderStatus.WAIT_ACCEPT, current.getUserId(), "需求发布，进入待接单"));

        // 广播新订单信号：需求大厅在线用户实时刷新列表，无需手动刷新页面
        webSocketSessionManager.broadcast("new_order");
        return order.getOrderId();
    }

    /** 修改待接单订单（R-11：仅待接单可改；已接单后禁止修改核心字段） */
    @Override
    @Transactional
    public void updateWaitOrder(OrderUpdateDTO dto) {
        User current = userService.getCurrentUser();
        Order order = orderMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // 权限：仅发布者可修改
        if (!order.getPublisherId().equals(current.getUserId())) {
            throw new BusinessException(403, "仅发布者可修改订单");
        }
        // R-11：仅待接单状态允许修改
        if (!OrderStatus.WAIT_ACCEPT.equals(order.getStatus())) {
            throw new BusinessException("订单已被接单，进入业务流转，不允许修改核心信息");
        }
        // 期望取件时间必须晚于当前时间
        validateExpectTime(dto.getExpectTime());
        // R-17：修改报酬同样受金额范围约束，避免绕过发布时的校验
        validateRewardRange(dto.getRewardAmount());
        // 取件码唯一：修改时排除本订单，避免与自身冲突
        validatePickupCodeUnique(dto.getPickupCode(), dto.getPickupLocation(), dto.getOrderId());
        order.setPickupCode(dto.getPickupCode());
        order.setExpressCompany(dto.getExpressCompany());
        order.setPickupLocation(dto.getPickupLocation());
        order.setDeliverLocation(dto.getDeliverLocation());
        order.setExpectTime(dto.getExpectTime());
        order.setRemark(dto.getRemark());
        // 报酬变更需同步调整冻结金额：提高报酬追加冻结（否则结算冻结不足），降低报酬解冻差额（否则多冻结不退回）
        BigDecimal oldReward = order.getRewardAmount();
        BigDecimal newReward = dto.getRewardAmount();
        if (newReward.compareTo(oldReward) != 0) {
            // 提高报酬意味着追加冻结（付更多钱），须校验支付密码；降低/不变无需付款
            if (newReward.compareTo(oldReward) > 0) {
                userService.verifyPayPassword(current.getUserId(), dto.getPayPassword());
            }
            walletService.adjustFrozenBalance(order.getPublisherId(), order.getOrderId(),
                    newReward.subtract(oldReward));
        }
        order.setRewardAmount(newReward);
        // updateById 带乐观锁 version 条件：影响行数 0 表示订单已被并发修改（典型场景：提交修改的同时订单被抢走）。
        // 必须抛异常让事务回滚——否则上面已执行的冻结/解冻调整会照常提交，
        // 造成「报酬未改、冻结额已变」的差额永久卡在冻结余额里无法退回
        if (orderMapper.updateById(order) == 0) {
            throw new BusinessException("订单已被接单或已被修改，请刷新后重试");
        }
    }

    /** 校验期望取件时间：必填且必须晚于当前时间，防止发布/修改时将取件时间设置到过去 */
    private void validateExpectTime(LocalDateTime expectTime) {
        if (expectTime == null) {
            throw new BusinessException("期望取件时间不能为空");
        }
        if (!expectTime.isAfter(LocalDateTime.now())) {
            throw new BusinessException("期望取件时间必须晚于当前时间，请重新选择");
        }
    }

    /** 校验报酬范围（R-17）：发布与修改待接单订单共用，防止修改时绕过金额下限/上限 */
    private void validateRewardRange(BigDecimal rewardAmount) {
        if (rewardAmount == null) {
            throw new BusinessException("报酬金额不能为空");
        }
        if (rewardAmount.compareTo(rules.getReward().getMin()) < 0
                || rewardAmount.compareTo(rules.getReward().getMax()) > 0) {
            throw new BusinessException("报酬金额需在 " + rules.getReward().getMin()
                    + " ~ " + rules.getReward().getMax() + " 元之间");
        }
    }

    /**
     * 校验取件码唯一性（仅限进行中订单）
     * 同一「取件码 + 取件地点」同一时刻只能存在一个进行中订单；已完成/已取消后释放，可再次发布
     * 取件码在不同取件点可能重复，故按「取件码 + 取件地点」组合判重
     * @param pickupCode     取件码（必填，取件凭据）
     * @param pickupLocation 取件地点
     * @param excludeOrderId 排除的订单ID（修改订单时排除自身，发布时为 null）
     */
    private void validatePickupCodeUnique(String pickupCode, String pickupLocation, Integer excludeOrderId) {
        if (pickupCode == null || pickupCode.isBlank()) {
            throw new BusinessException("取件码不能为空");
        }
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getPickupCode, pickupCode.trim())
                .eq(Order::getPickupLocation, pickupLocation)
                .in(Order::getStatus, List.of(OrderStatus.ACTIVE));
        if (excludeOrderId != null) {
            wrapper.ne(Order::getOrderId, excludeOrderId);
        }
        if (orderMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("该取件地点下已有相同取件码的进行中订单，请勿重复填写");
        }
    }

    /** 再次发布模板：复制历史订单的包裹信息（期望取件时间不预填，重新发布时应重新选择） */
    @Override
    public OrderReuseVO getReuseTemplate(Integer orderId) {
        User current = userService.getCurrentUser();
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // 权限：仅发布者本人可复制自己的订单
        if (!order.getPublisherId().equals(current.getUserId())) {
            throw new BusinessException(403, "仅发布者本人可查看订单模板");
        }
        OrderReuseVO vo = new OrderReuseVO();
        vo.setPickupCode(order.getPickupCode());
        vo.setExpressCompany(order.getExpressCompany());
        vo.setPickupLocation(order.getPickupLocation());
        vo.setDeliverLocation(order.getDeliverLocation());
        vo.setRemark(order.getRemark());
        vo.setRewardAmount(order.getRewardAmount());
        return vo;
    }

    /** 发布者提醒接单者（催单）：已接单/取件中/配送中可催，10 分钟内限一次防刷 */
    @Override
    @Transactional
    public void urgeTaker(Integer orderId) {
        User current = userService.getCurrentUser();
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // 权限：仅发布者本人
        if (!order.getPublisherId().equals(current.getUserId())) {
            throw new BusinessException(403, "仅发布者可提醒接单者");
        }
        // 状态：仅接单者履约中可催单（待接单无人可催；待收货/终态无需催）
        boolean active = OrderStatus.ACCEPTED.equals(order.getStatus())
                || OrderStatus.PICKING.equals(order.getStatus())
                || OrderStatus.DELIVERING.equals(order.getStatus());
        if (!active) {
            throw new BusinessException("订单当前状态（" + order.getStatus() + "）无需催单");
        }
        // 防刷：10 分钟内已提醒过则拒绝，避免频繁打扰接单者
        if (notificationService.recentlyUrged(order.getTakerId(), orderId)) {
            throw new BusinessException("10 分钟内已提醒过接单者，请耐心等待");
        }
        String action = OrderStatus.ACCEPTED.equals(order.getStatus()) ? "尽快取件" : "尽快送达";
        notificationService.send(order.getTakerId(), orderId,
                "发布者提醒：订单#" + orderId + " 请" + action + "（"
                        + order.getExpressCompany() + " " + order.getPickupCode() + "）");
    }

    /**
     * 取消订单（R-11 / R-12）
     * 待接单：发布者无条件取消；
     * 已接单/取件中：接单者申请取消 → 扣信誉分 5 → 订单退回待接单
     * 配送中及之后禁止取消
     */
    @Override
    @Transactional
    public void cancelOrder(Integer orderId, String cancelReason) {
        User current = userService.getCurrentUser();
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 场景一：待接单，发布者无条件取消（R-11），原因必选发布者清单
        if (OrderStatus.WAIT_ACCEPT.equals(order.getStatus())) {
            if (!order.getPublisherId().equals(current.getUserId())) {
                throw new BusinessException(403, "仅发布者可取消待接单订单");
            }
            if (!CancelReason.PUBLISHER_REASONS.contains(cancelReason)) {
                throw new BusinessException("请选择有效的取消原因（发布者原因清单）");
            }
            // 状态变更带原状态守卫 + 推进版本号：与定时任务「过期未接单自动取消」互斥。
            // 两者若各自通过检查再各解冻一次，同一订单会被重复退款（资金泄漏），
            // 故必须以 status='待接单' 为更新条件，保证只有一条路径能赢得这次状态流转
            int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getOrderId, orderId)
                    .eq(Order::getStatus, OrderStatus.WAIT_ACCEPT)
                    .set(Order::getStatus, OrderStatus.CANCELLED)
                    .set(Order::getCancelReason, cancelReason)
                    .set(Order::getUpdateTime, LocalDateTime.now())
                    .set(Order::getVersion, order.getVersion() + 1));
            if (rows == 0) {
                throw new BusinessException("订单状态已变更，请刷新后重试");
            }
            historyService.saveHistoryRecord(OrderDomainSupport.buildHistory(orderId, OrderStatus.WAIT_ACCEPT,
                    OrderStatus.CANCELLED, current.getUserId(), "发布者取消（" + cancelReason + "）"));
            // R-27：冻结报酬解冻退回发布者可用余额
            walletService.unfreezeBalance(order.getPublisherId(), orderId, order.getRewardAmount());
            return;
        }

        // 场景二：已接单/取件中，接单者申请取消（R-12），原因必选接单者清单
        if (OrderStatus.ACCEPTED.equals(order.getStatus())
                || OrderStatus.PICKING.equals(order.getStatus())) {
            if (!current.getUserId().equals(order.getTakerId())) {
                throw new BusinessException(403, "仅接单者可申请取消已接订单");
            }
            // 转单接力中不允许直接取消（避免接力挂单悬空），须先撤回转单
            if (TransferStatus.PENDING.equals(order.getTransferStatus())) {
                throw new BusinessException("订单正在转单接力中，请先撤回转单接力再取消订单");
            }
            if (!CancelReason.TAKER_REASONS.contains(cancelReason)) {
                throw new BusinessException("请选择有效的取消原因（接单者原因清单）");
            }
            String fromStatus = order.getStatus();
            // R-12：接单后取消属违约，扣信誉分 5 分（同时写流水明细）
            userService.updateCreditScore(current.getUserId(),
                    -rules.getCredit().getBreachDeduct(),
                    "接单者取消",
                    "接单者取消订单（订单#" + orderId + "，" + cancelReason + "），违约扣信誉分5",
                    orderId);
            // 订单退回待接单，重新开放给其他接单者
            // 清空接单者：订单重新开放后原接单者不再持有该单（避免幻影 taker 撑大并发单数）
            // 注意：updateById 默认不更新 null 字段，须用 UpdateWrapper 显式置空 taker_id
            orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getOrderId, orderId)
                    .set(Order::getStatus, OrderStatus.WAIT_ACCEPT)
                    .set(Order::getCancelReason, cancelReason)
                    .set(Order::getTakerId, null)
                    .set(Order::getUpdateTime, LocalDateTime.now()));
            historyService.saveHistoryRecord(OrderDomainSupport.buildHistory(orderId, fromStatus,
                    OrderStatus.WAIT_ACCEPT, current.getUserId(), "接单者取消（" + cancelReason + "），扣信誉分5，退回待接单"));
            // 报酬保持冻结：订单仍处待接单、未最终取消，冻结资金继续为该单服务（与定时任务②超时自动取消行为一致），
            // 仅在发布者最终取消（场景一解冻一次）或订单完成结算时释放，避免同一订单重复解冻
            // 通知发布者订单重新开放（R-5）
            notificationService.send(order.getPublisherId(), orderId,
                    OrderDomainSupport.reopenNotice(order, "您的订单已被接单者取消（" + cancelReason + "），重新进入待接单，请关注"));
            return;
        }

        // 场景三：配送中及之后禁止取消
        throw new BusinessException("订单当前状态（" + order.getStatus() + "）不允许取消");
    }

    /**
     * 抢单核心（R-6 ~ R-10）：乐观锁 + 事务，同一订单只能被一人成功承接
     */
    @Override
    @Transactional
    public void takeOrder(Integer orderId) {
        User taker = userService.getCurrentUser();
        // 角色权限：仅接单者（canPublish=false）可抢单接单（页面与接口双重校验）
        if (Boolean.TRUE.equals(taker.getCanPublish())) {
            throw new BusinessException(403, "当前账号为发布者角色，不可抢单接单");
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // R-6：只有待接单状态可接
        if (!OrderStatus.WAIT_ACCEPT.equals(order.getStatus())) {
            throw new BusinessException("该订单已被接单，无法重复承接");
        }
        // 期望取件时间已过：过期需求不可再接，避免接单后立即触发超时取消
        if (order.getExpectTime() != null && order.getExpectTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("该订单期望取件时间已过，无法接单");
        }
        // R-8：禁止接自己发布的订单
        if (order.getPublisherId().equals(taker.getUserId())) {
            throw new BusinessException("不能承接自己发布的订单");
        }
        // R-23：信誉分低于阈值禁止接单
        if (taker.getCreditScore() < rules.getCredit().getBanThreshold()) {
            throw new BusinessException("信誉分低于 " + rules.getCredit().getBanThreshold()
                    + "，暂不可接单，请先提升信誉分");
        }
        // R-9：校验同时进行中订单数量不超过等级上限
        Long activeCount = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getTakerId, taker.getUserId())
                .in(Order::getStatus, List.of(OrderStatus.ACTIVE)));
        int limit = CreditLevelUtil.concurrentLimit(taker.getCreditScore(), rules);
        if (activeCount >= limit) {
            throw new BusinessException("当前信誉等级（" + CreditLevelUtil.levelOf(taker.getCreditScore(), rules)
                    + "）同时进行中订单已达上限 " + limit + " 单，无法继续接单");
        }

        // R-7：乐观锁更新 —— status='待接单' AND version=读取时版本号
        int rows = orderMapper.optimisticTakeOrder(orderId, taker.getUserId(), order.getVersion());
        if (rows == 0) {
            throw new BusinessException("手慢了，该订单已被别人抢走，抢单失败");
        }

        // 抢单成功：写状态历史 待接单 → 已接单（R-4）
        historyService.saveHistoryRecord(OrderDomainSupport.buildHistory(orderId, OrderStatus.WAIT_ACCEPT,
                OrderStatus.ACCEPTED, taker.getUserId(), "接单者 " + taker.getUsername() + " 抢单成功"));
        // 通知发布者（R-5）
        notificationService.send(order.getPublisherId(), orderId, "您的订单已被 " + taker.getUsername() + " 接单");
    }

    /**
     * 状态流转（R-1 ~ R-5）
     * - R-1：按状态机严格流转，禁止跨跳（含 配送中→待收货→已完成 两段式确认）
     * - R-2：取消走 cancelOrder
     * - R-3：已完成仅需求方确认收货触发（待收货状态，24 小时超时由定时任务自动完成）
     * - R-4：变更必须写历史表
     * - R-5：变更后通知相关方
     */
    @Override
    @Transactional
    public void updateOrderStatus(Integer orderId, String targetStatus, Integer operatorId, String changeNote, boolean skipPhoto) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // 转单接力中：接单者不可推进状态（须先撤回接力或等待他人接力），避免接力挂单与本人履约并行
        if (TransferStatus.PENDING.equals(order.getTransferStatus())
                && operatorId.equals(order.getTakerId())) {
            throw new BusinessException("订单正在转单接力中，请先撤回转单或等待接力完成");
        }
        String from = order.getStatus();

        // R-1：校验当前状态是否允许流转到目标状态
        List<String> allowed = ALLOWED_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(targetStatus)) {
            throw new BusinessException("非法状态流转：" + from + " → " + targetStatus + "（禁止跨状态跳转）");
        }

        // 权限校验：
        // - 待收货 → 已完成：仅需求发布者触发（R-3 确认收货）
        // - 已接单 → 取件中 / 取件中 → 配送中 / 配送中 → 待收货：仅接单者触发（敏感操作鉴权）
        if (OrderStatus.COMPLETED.equals(targetStatus)) {
            if (!order.getPublisherId().equals(operatorId)) {
                throw new BusinessException(403, "确认收货仅限需求发布者操作");
            }
        } else if (!operatorId.equals(order.getTakerId())) {
            throw new BusinessException(403, "订单状态更新仅限接单者操作");
        }

        // 配送中 → 待收货：默认要求上传送达照片，无法拍照时可显式 skipPhoto 跳过，并记录确认送达时间
        if (OrderStatus.WAIT_CONFIRM.equals(targetStatus)) {
            checkDeliverPhoto(order, skipPhoto);
            order.setConfirmArrivalTime(LocalDateTime.now());
        }

        // 执行状态变更：updateById 带乐观锁 version 条件，影响行数 0 表示订单已被并发修改
        // （典型场景：发布者确认收货的同时，定时任务也判定超时并自动确认）。
        // 必须检查返回值并终止，否则两条路径都会继续执行结算，造成重复扣款/重复入账。
        order.setStatus(targetStatus);
        if (orderMapper.updateById(order) == 0) {
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }
        // R-4：写状态历史
        historyService.saveHistoryRecord(OrderDomainSupport.buildHistory(orderId, from, targetStatus, operatorId, changeNote));
        // R-5：通知另一方
        if (OrderStatus.COMPLETED.equals(targetStatus)) {
            // R-28：订单完成，冻结报酬结算 —— 扣发布者冻结，接单者入账，生成双方流水
            walletService.settleOrderReward(order.getPublisherId(), order.getTakerId(), orderId, order.getRewardAmount());
            notificationService.send(order.getTakerId(), orderId, "订单已完成，报酬已结算，请及时评价");
        } else if (OrderStatus.WAIT_CONFIRM.equals(targetStatus)) {
            notificationService.send(order.getPublisherId(), orderId, "包裹已送达，请尽快确认收货（"
                    + rules.getTimeout().getConfirmReceiptHours() + " 小时内未确认将自动确认收货）");
        } else {
            notificationService.send(order.getPublisherId(), orderId, "您的订单状态更新为：" + targetStatus);
        }
    }

    /** 校验送达照片：默认要求上传，无法拍照时可显式跳过；未上传且未跳过时拒绝流转到「待收货」 */
    private void checkDeliverPhoto(Order order, boolean skipPhoto) {
        if (order.getDeliverPhoto() == null && !skipPhoto) {
            throw new BusinessException("请先上传送达照片，或选择「无法拍照」后继续");
        }
    }

    /**
     * 接单者确认送达（配送中 → 待收货）
     * 权限：仅接单者本人；记录确认送达时间，需求方须在 confirmReceiptHours 内确认收货，超时由定时任务自动确认
     */
    @Override
    @Transactional
    public void confirmArrival(Integer orderId, boolean skipPhoto) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // 仅配送中状态可确认送达
        if (!OrderStatus.DELIVERING.equals(order.getStatus())) {
            throw new BusinessException("当前状态（" + order.getStatus() + "）不可确认送达");
        }
        Integer operatorId = userService.getCurrentUser().getUserId();
        // 仅接单者本人
        if (!operatorId.equals(order.getTakerId())) {
            throw new BusinessException(403, "仅接单者可确认送达");
        }
        // 默认要求送达照片：无法拍照时须显式 skipPhoto 跳过（「无法拍照」入口）
        checkDeliverPhoto(order, skipPhoto);
        order.setStatus(OrderStatus.WAIT_CONFIRM);
        order.setConfirmArrivalTime(LocalDateTime.now());
        orderMapper.updateById(order);
        String note = order.getDeliverPhoto() != null
                ? "接单者确认送达（已上传送达凭证），等待需求方确认收货"
                : "接单者确认送达（无法拍照，已跳过凭证），等待需求方确认收货";
        historyService.saveHistoryRecord(OrderDomainSupport.buildHistory(orderId, OrderStatus.DELIVERING,
                OrderStatus.WAIT_CONFIRM, operatorId, note));
        notificationService.send(order.getPublisherId(), orderId, "包裹已送达，请尽快确认收货（"
                + rules.getTimeout().getConfirmReceiptHours() + " 小时内未确认将自动确认收货）");
    }

    /**
     * 上传订单照片凭证：type=pickup（取件，选填）/ deliver（送达，默认要求，无法拍照可跳过）
     * 权限：仅订单接单者本人；pickup 允许在已接单/取件中上传，deliver 允许在配送中上传
     */
    @Override
    @Transactional
    public String uploadOrderPhoto(Integer orderId, String type, MultipartFile file) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        Integer operatorId = userService.getCurrentUser().getUserId();
        if (!operatorId.equals(order.getTakerId())) {
            throw new BusinessException(403, "仅接单者可上传订单照片凭证");
        }
        boolean pickup = "pickup".equals(type);
        boolean deliver = "deliver".equals(type);
        if (!pickup && !deliver) {
            throw new BusinessException("照片类型仅支持 pickup（取件）/ deliver（送达）");
        }
        if (pickup && !OrderStatus.ACCEPTED.equals(order.getStatus())
                && !OrderStatus.PICKING.equals(order.getStatus())) {
            throw new BusinessException("当前状态（" + order.getStatus() + "）不可上传取件照片");
        }
        if (deliver && !OrderStatus.DELIVERING.equals(order.getStatus())) {
            throw new BusinessException("当前状态（" + order.getStatus() + "）不可上传送达照片");
        }
        String path = savePhoto(file);
        // updateById 默认不更新 null 字段，此处仅需写入单个字段，用 UpdateWrapper 显式 set 更清晰
        LambdaUpdateWrapper<Order> uw = new LambdaUpdateWrapper<Order>()
                .eq(Order::getOrderId, orderId)
                .set(Order::getUpdateTime, LocalDateTime.now());
        if (pickup) {
            uw.set(Order::getPickupPhoto, path);
        } else {
            uw.set(Order::getDeliverPhoto, path);
        }
        orderMapper.update(null, uw);
        return path;
    }

    /** 保存订单照片到 uploads/order/：校验格式（jpg/png/gif/webp）与大小（≤5MB），UUID 重命名防重名 */
    private String savePhoto(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        int dot = original == null ? -1 : original.lastIndexOf('.');
        if (dot >= 0) {
            ext = original.substring(dot).toLowerCase(Locale.ROOT);
        }
        List<String> allowed = Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".webp");
        if (!allowed.contains(ext)) {
            throw new BusinessException("仅支持 jpg/png/gif/webp 格式的图片");
        }
        // 内容校验：读取文件头确认是真实图片，防止改扩展名的伪装文件落地
        String realType = ImageUtil.detectImageType(file);
        if (realType == null) {
            throw new BusinessException("图片内容校验失败，请上传真实的 jpg/png/gif/webp 图片");
        }
        // 以真实类型作为落盘扩展名，不信任原始文件名
        ext = realType;
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException("图片大小不能超过 5MB");
        }
        File dir = new File(System.getProperty("user.dir"), "uploads/order");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException("照片目录创建失败，请检查目录权限");
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        try {
            file.transferTo(new File(dir, filename));
        } catch (IOException e) {
            throw new BusinessException("照片保存失败，请重试");
        }
        return "/uploads/order/" + filename;
    }

    /** 需求大厅分页：仅待接单，支持关键词/报酬范围筛选（3.8） */
    @Override
    public IPage<OrderVO> getHallOrderPage(long current, long size, String keyword,
                                           BigDecimal minReward, BigDecimal maxReward) {
        // 空串关键词归一化为 null，避免无意义过滤
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return orderMapper.selectHallPage(new Page<>(current, size), kw, minReward, maxReward);
    }

    /** 我的发布分页（3.7，按状态分组） */
    @Override
    public IPage<OrderVO> getMyPublishPage(long current, long size, List<String> statusList) {
        Integer userId = userService.getCurrentUser().getUserId();
        Page<OrderVO> page = new Page<>(current, size);
        // 状态分组条件：无参=全部，传参=指定状态列表
        IPage<OrderVO> result = orderMapper.selectMyPage(page, userId, null, statusList);
        fillEvaluatedFlag(result.getRecords(), userId);
        return result;
    }

    /** 我的接单分页 */
    @Override
    public IPage<OrderVO> getMyTakePage(long current, long size, List<String> statusList) {
        Integer userId = userService.getCurrentUser().getUserId();
        Page<OrderVO> page = new Page<>(current, size);
        IPage<OrderVO> result = orderMapper.selectMyPage(page, null, userId, statusList);
        fillEvaluatedFlag(result.getRecords(), userId);
        return result;
    }

    /**
     * 填充「当前用户是否已评价」标记：仅对已完成订单批量查询评价记录，
     * 供前端展示「去评价 / 已评价」，避免逐条查询
     */
    private void fillEvaluatedFlag(List<OrderVO> records, Integer userId) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Integer> completedIds = records.stream()
                .filter(o -> OrderStatus.COMPLETED.equals(o.getStatus()))
                .map(Order::getOrderId)
                .toList();
        if (completedIds.isEmpty()) {
            return;
        }
        List<Evaluation> evals = evaluationMapper.selectList(new LambdaQueryWrapper<Evaluation>()
                .eq(Evaluation::getEvaluatorId, userId)
                .in(Evaluation::getOrderId, completedIds));
        java.util.Set<Integer> evaluatedIds = new java.util.HashSet<>();
        for (Evaluation e : evals) {
            evaluatedIds.add(e.getOrderId());
        }
        for (OrderVO o : records) {
            if (OrderStatus.COMPLETED.equals(o.getStatus())) {
                o.setMyEvaluated(evaluatedIds.contains(o.getOrderId()));
            }
        }
    }

    /**
     * 订单详情：订单 + 双方昵称 + 完整状态流转 + 双向评价（3.7）
     * 权限：订单发布者/接单者可查看全部信息；非参与者仅在订单处于公开展示状态
     * （待接单 / 转单接力中）时可预览，且对取件码、履约照片脱敏——
     * 取件码是取件凭据、照片是履约凭证，若任意登录用户可查，等于凭证公开、可凭码取件
     */
    @Override
    public OrderDetailVO getOrderVOById(Integer orderId) {
        OrderVO vo = orderMapper.selectOrderVOById(orderId);
        if (vo == null) {
            throw new BusinessException("订单不存在");
        }
        Integer currentUserId = userService.getCurrentUser().getUserId();
        boolean participant = currentUserId.equals(vo.getPublisherId())
                || (vo.getTakerId() != null && currentUserId.equals(vo.getTakerId()));
        if (!participant) {
            // 非参与者仅可预览大厅/接力专区中公开展示的订单，其余（进行中/历史）一律拒绝，防止遍历越权
            boolean publicVisible = OrderStatus.WAIT_ACCEPT.equals(vo.getStatus())
                    || TransferStatus.PENDING.equals(vo.getTransferStatus());
            if (!publicVisible) {
                throw new BusinessException(403, "无权查看该订单详情");
            }
            // 脱敏：取件码与履约照片仅订单双方可见
            vo.setPickupCode(null);
            vo.setPickupPhoto(null);
            vo.setDeliverPhoto(null);
        }
        OrderDetailVO detail = new OrderDetailVO();
        detail.setOrder(vo);
        detail.setStatusHistory(historyService.listByOrderId(orderId));
        detail.setEvaluations(evaluationMapper.selectByOrderId(orderId));
        return detail;
    }
}
package com.campus.express.service.impl;

import com.campus.express.common.BusinessException;
import com.campus.express.common.OrderStatus;
import com.campus.express.common.TransferStatus;
import com.campus.express.entity.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 定时任务单笔处理逻辑单元测试（④ 原状态守卫 / ⑤ 按单独立事务的异常隔离）
 *
 * <p>被测方法为超时调度领域服务 {@link OrderTimeoutServiceImpl} 的私有单笔处理器，通过反射直接调用，
 * 精确验证「影响行数为 0 时跳过该单、不扣信誉分、不解冻」的原状态守卫语义，
 * 以及结算失败不阻塞订单完成的防御逻辑。</p>
 */
class OrderServiceImplScheduledTaskTest extends AbstractOrderServiceTest {

    // ---------------- 任务②：已接单超时未取件（handleStuckAcceptedOrder） ----------------

    @Test
    @DisplayName("未取件超时：影响行数为 0（订单已被推进/取消）→ 跳过，不扣信誉分、不通知")
    void stuckOrderGuardSkipsWhenRowsZero() {
        Order o = order(10, OrderStatus.ACCEPTED, 1, 2);
        when(orderMapper.update(any(), any())).thenReturn(0);

        ReflectionTestUtils.invokeMethod(timeout, "handleStuckAcceptedOrder", o);

        verify(userService, never()).updateCreditScore(any(), anyInt(), anyString(), anyString(), any());
        verify(historyService, never()).saveHistoryRecord(any());
        verify(notificationService, never()).send(any(), any(), anyString());
    }

    @Test
    @DisplayName("未取件超时：状态流转成功 → 扣信誉分5、退回待接单、通知双方")
    void stuckOrderDeductsCreditWhenUpdated() {
        Order o = order(10, OrderStatus.ACCEPTED, 1, 2);
        when(orderMapper.update(any(), any())).thenReturn(1);

        ReflectionTestUtils.invokeMethod(timeout, "handleStuckAcceptedOrder", o);

        verify(userService).updateCreditScore(eq(2), eq(-5), eq("接单超时"), anyString(), eq(10));
        verify(historyService).saveHistoryRecord(any());
        verify(notificationService).send(eq(1), eq(10), anyString());
        verify(notificationService).send(eq(2), eq(10), anyString());
    }

    @Test
    @DisplayName("未取件超时：接力中的订单通知需说明接力已失效")
    void stuckOrderNotifiesRelayInvalidation() {
        Order o = order(10, OrderStatus.ACCEPTED, 1, 2);
        o.setTransferStatus(TransferStatus.PENDING);
        when(orderMapper.update(any(), any())).thenReturn(1);

        ReflectionTestUtils.invokeMethod(timeout, "handleStuckAcceptedOrder", o);

        verify(notificationService).send(eq(2), eq(10),
                org.mockito.ArgumentMatchers.contains("转单接力已随之失效"));
    }

    // ---------------- 任务③：取件后超时未送达（handleOverdueDeliveryOrder） ----------------

    @Test
    @DisplayName("未送达超时：影响行数为 0 → 跳过，不扣分、不解冻")
    void overdueDeliveryGuardSkipsWhenRowsZero() {
        Order o = order(10, OrderStatus.PICKING, 1, 2);
        when(orderMapper.update(any(), any())).thenReturn(0);

        ReflectionTestUtils.invokeMethod(timeout, "handleOverdueDeliveryOrder", o);

        verify(userService, never()).updateCreditScore(any(), anyInt(), anyString(), anyString(), any());
        verify(walletService, never()).unfreezeBalance(any(), any(), any());
    }

    @Test
    @DisplayName("未送达超时：状态流转成功 → 扣信誉分5、解冻报酬、通知双方")
    void overdueDeliveryDeductsAndUnfreezes() {
        Order o = order(10, OrderStatus.PICKING, 1, 2);
        when(orderMapper.update(any(), any())).thenReturn(1);

        ReflectionTestUtils.invokeMethod(timeout, "handleOverdueDeliveryOrder", o);

        verify(userService).updateCreditScore(eq(2), eq(-5), eq("配送超时"), anyString(), eq(10));
        verify(walletService).unfreezeBalance(eq(1), eq(10), any());
        verify(notificationService).send(eq(1), eq(10), anyString());
        verify(notificationService).send(eq(2), eq(10), anyString());
    }

    // ---------------- 自动确认收货（autoConfirmOneOrder） ----------------

    @Test
    @DisplayName("自动确认收货：updateById 返回 0（发布者已抢先确认）→ 跳过，不重复结算")
    void autoConfirmSkipsWhenAlreadyConfirmed() {
        Order o = order(10, OrderStatus.WAIT_CONFIRM, 1, 2);
        when(orderMapper.updateById(any(Order.class))).thenReturn(0);

        ReflectionTestUtils.invokeMethod(timeout, "autoConfirmOneOrder", o);

        verify(walletService, never()).settleOrderReward(any(), any(), any(), any());
        verify(notificationService, never()).send(any(), any(), anyString());
    }

    @Test
    @DisplayName("自动确认收货：正常结算并通知双方")
    void autoConfirmSettlesAndNotifies() {
        Order o = order(10, OrderStatus.WAIT_CONFIRM, 1, 2);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        ReflectionTestUtils.invokeMethod(timeout, "autoConfirmOneOrder", o);

        assertEquals(OrderStatus.COMPLETED, o.getStatus());
        verify(walletService).settleOrderReward(eq(1), eq(2), eq(10), any());
        verify(notificationService).send(eq(1), eq(10), anyString());
        verify(notificationService).send(eq(2), eq(10), anyString());
    }

    @Test
    @DisplayName("自动确认收货：结算失败不阻塞订单完成（防御约束）")
    void autoConfirmToleratesSettlementFailure() {
        Order o = order(10, OrderStatus.WAIT_CONFIRM, 1, 2);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);
        doThrow(new BusinessException("冻结余额不足"))
                .when(walletService).settleOrderReward(any(), any(), any(), any());

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(timeout, "autoConfirmOneOrder", o));

        assertEquals(OrderStatus.COMPLETED, o.getStatus());
        verify(notificationService).send(eq(1), eq(10), anyString());
    }

    // ---------------- 过期未接单自动取消（cancelExpiredWaitOrders） ----------------

    @Test
    @DisplayName("过期未接单：无过期订单 → 返回 0")
    void cancelExpiredReturnsZeroWhenNone() {
        when(orderMapper.selectList(any())).thenReturn(List.of());
        Integer n = ReflectionTestUtils.invokeMethod(timeout, "cancelExpiredWaitOrders", LocalDateTime.now());
        assertEquals(0, n);
    }

    @Test
    @DisplayName("过期未接单：状态守卫成功 → 解冻报酬、写历史、通知发布者")
    void cancelExpiredUnfreezesAndNotifies() {
        Order o = order(10, OrderStatus.WAIT_ACCEPT, 1, null);
        when(orderMapper.selectList(any())).thenReturn(List.of(o));
        when(orderMapper.update(any(), any())).thenReturn(1);

        Integer n = ReflectionTestUtils.invokeMethod(timeout, "cancelExpiredWaitOrders", LocalDateTime.now());

        assertEquals(1, n);
        verify(walletService).unfreezeBalance(eq(1), eq(10), any());
        verify(historyService).saveHistoryRecord(any());
        verify(notificationService).send(eq(1), eq(10), anyString());
    }

    @Test
    @DisplayName("过期未接单：状态守卫影响行数为 0（已被抢单）→ 不解冻，避免重复退款")
    void cancelExpiredSkipsWhenTaken() {
        Order o = order(10, OrderStatus.WAIT_ACCEPT, 1, null);
        when(orderMapper.selectList(any())).thenReturn(List.of(o));
        when(orderMapper.update(any(), any())).thenReturn(0);

        Integer n = ReflectionTestUtils.invokeMethod(timeout, "cancelExpiredWaitOrders", LocalDateTime.now());

        assertEquals(0, n);
        verify(walletService, never()).unfreezeBalance(any(), any(), any());
    }

    // ---------------- 任务编排：timeoutRemindCancelTask ----------------

    @Test
    @DisplayName("超时任务编排：未取件订单在状态守卫失败时不扣信誉分（按单独立处理不中断）")
    void timeoutTaskRespectsStuckOrderGuard() {
        Order stuck = order(10, OrderStatus.ACCEPTED, 1, 2);
        // selectList 调用顺序：⓪过期未接单 / ①超时提醒 / ②未取件 / ③未送达
        when(orderMapper.selectList(any()))
                .thenReturn(List.of(), List.of(), List.of(stuck), List.of());
        when(orderMapper.update(any(), any())).thenReturn(0);

        service.timeoutRemindCancelTask();

        verify(userService, never()).updateCreditScore(any(), anyInt(), anyString(), anyString(), any());
    }
}
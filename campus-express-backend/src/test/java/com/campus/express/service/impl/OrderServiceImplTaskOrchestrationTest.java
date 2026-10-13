package com.campus.express.service.impl;

import com.campus.express.common.OrderStatus;
import com.campus.express.common.TransferStatus;
import com.campus.express.entity.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 定时任务编排层单元测试（任务扫描 → 逐单派发）
 *
 * <p>覆盖 {@code relayTimeoutTask}（接力超时自动撤回）、{@code autoConfirmReceiptTask}
 * （待收货超时自动确认收货，走 runPerOrderTx 独立事务）、{@code timeoutRemindCancelTask}
 * （过期未接单自动取消 / 无人接单提醒幂等 / 未取件与未送达超时自动取消的编排）。</p>
 */
class OrderServiceImplTaskOrchestrationTest extends AbstractOrderServiceTest {

    // ---------------- relayTimeoutTask：接力超时自动撤回 ----------------

    @Test
    @DisplayName("接力超时：无待接力订单 → 不做任何处理")
    void relayTimeoutNoPending() {
        when(orderMapper.selectList(any())).thenReturn(List.of());

        service.relayTimeoutTask();

        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("接力超时：自动撤回接力标记，关闭流水并通知双方、广播")
    void relayTimeoutWithdrawsPending() {
        Order o = order(10, OrderStatus.ACCEPTED, 1, 2);
        o.setTransferStatus(TransferStatus.PENDING);
        when(orderMapper.selectList(any())).thenReturn(List.of(o));

        service.relayTimeoutTask();

        verify(orderMapper).update(any(), any());
        verify(transferMapper).update(any(), any());
        verify(historyService).saveHistoryRecord(any());
        verify(notificationService).send(eq(2), eq(10), anyString());
        verify(notificationService).send(eq(1), eq(10), anyString());
        verify(webSocketSessionManager).broadcast("new_relay");
    }

    // ---------------- autoConfirmReceiptTask：待收货超时自动确认 ----------------

    @Test
    @DisplayName("自动确认收货：无超时订单 → 不处理任何订单")
    void autoConfirmNoPending() {
        when(orderMapper.selectList(any())).thenReturn(List.of());

        service.autoConfirmReceiptTask();

        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    @DisplayName("自动确认收货：超时订单在独立事务中完成并结算、通知双方")
    void autoConfirmProcessesPending() {
        Order o = order(10, OrderStatus.WAIT_CONFIRM, 1, 2);
        o.setConfirmArrivalTime(LocalDateTime.now().minusHours(30));
        when(orderMapper.selectList(any())).thenReturn(List.of(o));
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        service.autoConfirmReceiptTask();

        verify(walletService).settleOrderReward(eq(1), eq(2), eq(10), any());
        verify(notificationService).send(eq(1), eq(10), anyString());
        verify(notificationService).send(eq(2), eq(10), anyString());
    }

    // ---------------- timeoutRemindCancelTask：提醒与超时取消编排 ----------------

    @Test
    @DisplayName("超时任务：无人接单提醒已发送过（幂等）→ 不重复提醒")
    void timeoutTaskReminderIsIdempotent() {
        Order wait = order(11, OrderStatus.WAIT_ACCEPT, 1, null);
        // selectList 调用顺序：⓪过期未接单 / ①超时提醒 / ②未取件 / ③未送达
        when(orderMapper.selectList(any())).thenReturn(List.of(), List.of(wait), List.of(), List.of());
        when(notificationService.hasNoTakerReminder(11)).thenReturn(true);

        service.timeoutRemindCancelTask();

        verify(notificationService, never()).send(any(), any(), anyString());
    }

    @Test
    @DisplayName("超时任务：无人接单提醒未发送过 → 发送一次提醒")
    void timeoutTaskSendsReminderOnce() {
        Order wait = order(11, OrderStatus.WAIT_ACCEPT, 1, null);
        when(orderMapper.selectList(any())).thenReturn(List.of(), List.of(wait), List.of(), List.of());
        when(notificationService.hasNoTakerReminder(11)).thenReturn(false);

        service.timeoutRemindCancelTask();

        verify(notificationService).send(eq(1), eq(11), anyString());
    }

    @Test
    @DisplayName("超时任务：未取件与未送达订单分别按正确路径处理（扣分/解冻）")
    void timeoutTaskProcessesStuckAndOverdue() {
        Order stuck = order(10, OrderStatus.ACCEPTED, 1, 2);
        Order overdue = order(12, OrderStatus.PICKING, 1, 3);
        when(orderMapper.selectList(any()))
                .thenReturn(List.of(), List.of(), List.of(stuck), List.of(overdue));
        when(orderMapper.update(any(), any())).thenReturn(1);

        service.timeoutRemindCancelTask();

        verify(userService).updateCreditScore(eq(2), eq(-5), anyString(), anyString(), eq(10));
        verify(userService).updateCreditScore(eq(3), eq(-5), anyString(), anyString(), eq(12));
        verify(walletService).unfreezeBalance(eq(1), eq(12), any());
    }
}
package com.campus.express.service.impl;

import com.campus.express.common.BusinessException;
import com.campus.express.common.OrderStatus;
import com.campus.express.common.TransferStatus;
import com.campus.express.entity.Order;
import com.campus.express.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 抢单（R-6 ~ R-10）与状态机流转（R-1 ~ R-5）单元测试
 * 覆盖 ① 乐观锁抢单守卫、③ 状态流转并发重复结算防护
 */
class OrderServiceImplTakeAndStatusTest extends AbstractOrderServiceTest {

    // ---------------- takeOrder：抢单守卫 ----------------

    @Test
    @DisplayName("抢单：发布者角色不可抢单（403）")
    void takerMustBeNonPublisher() {
        when(userService.getCurrentUser()).thenReturn(user(2, "发布者", 100, true));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.takeOrder(10));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("抢单：订单不存在")
    void takeOrderNotFound() {
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.takeOrder(10));
    }

    @Test
    @DisplayName("抢单：非待接单状态拒绝重复承接")
    void takeOrderRejectsNonWaitAccept() {
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 3));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.takeOrder(10));
        assertTrue(ex.getMessage().contains("已被接单"));
    }

    @Test
    @DisplayName("抢单：期望取件时间已过不可接单")
    void takeOrderRejectsExpiredExpectTime() {
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        Order o = order(10, OrderStatus.WAIT_ACCEPT, 1, null);
        o.setExpectTime(LocalDateTime.now().minusMinutes(1));
        when(orderMapper.selectById(10)).thenReturn(o);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.takeOrder(10));
        assertTrue(ex.getMessage().contains("期望取件时间已过"));
    }

    @Test
    @DisplayName("抢单：禁止承接自己发布的订单")
    void takeOrderRejectsOwnOrder() {
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.WAIT_ACCEPT, 2, null));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.takeOrder(10));
        assertTrue(ex.getMessage().contains("自己发布"));
    }

    @Test
    @DisplayName("抢单：信誉分低于阈值（80）禁止接单")
    void takeOrderRejectsLowCredit() {
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 70, false));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.WAIT_ACCEPT, 1, null));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.takeOrder(10));
        assertTrue(ex.getMessage().contains("信誉分"));
    }

    @Test
    @DisplayName("抢单：进行中订单达等级上限（良好=3）拒绝")
    void takeOrderRejectsConcurrentLimit() {
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.WAIT_ACCEPT, 1, null));
        when(orderMapper.selectCount(any())).thenReturn(3L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.takeOrder(10));
        assertTrue(ex.getMessage().contains("上限"));
    }

    @Test
    @DisplayName("抢单：乐观锁影响行数为 0 → 抢单失败")
    void takeOrderLosesRace() {
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.WAIT_ACCEPT, 1, null));
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(orderMapper.optimisticTakeOrder(any(), any(), any())).thenReturn(0);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.takeOrder(10));
        assertTrue(ex.getMessage().contains("抢单失败"));
    }

    @Test
    @DisplayName("抢单成功：写状态历史并通知发布者")
    void takeOrderSuccess() {
        User taker = user(2, "接单者", 100, false);
        when(userService.getCurrentUser()).thenReturn(taker);
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.WAIT_ACCEPT, 1, null));
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(orderMapper.optimisticTakeOrder(eq(10), eq(2), eq(0))).thenReturn(1);

        service.takeOrder(10);

        verify(historyService).saveHistoryRecord(any());
        verify(notificationService).send(eq(1), eq(10), anyString());
    }

    // ---------------- updateOrderStatus：状态机与权限 ----------------

    @Test
    @DisplayName("状态流转：禁止跨状态跳转（已接单 → 配送中）")
    void rejectIllegalTransition() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateOrderStatus(10, OrderStatus.DELIVERING, 2, "跳过取件中", false));
        assertTrue(ex.getMessage().contains("非法状态流转"));
    }

    @Test
    @DisplayName("状态流转：接力中的订单接单者不可推进状态")
    void takerCannotAdvanceWhileRelaying() {
        Order o = order(10, OrderStatus.ACCEPTED, 1, 2);
        o.setTransferStatus(TransferStatus.PENDING);
        when(orderMapper.selectById(10)).thenReturn(o);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateOrderStatus(10, OrderStatus.PICKING, 2, "推进", false));
        assertTrue(ex.getMessage().contains("转单接力"));
    }

    @Test
    @DisplayName("状态流转：确认收货仅限发布者（403）")
    void confirmReceiptOnlyPublisher() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.WAIT_CONFIRM, 1, 2));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateOrderStatus(10, OrderStatus.COMPLETED, 2, "确认收货", false));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("状态流转：履约推进仅限接单者（403）")
    void advanceOnlyTaker() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateOrderStatus(10, OrderStatus.PICKING, 1, "推进", false));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("确认送达：未上传送达照片且未跳过 → 拒绝流转到待收货")
    void rejectArrivalWithoutPhoto() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.DELIVERING, 1, 2));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateOrderStatus(10, OrderStatus.WAIT_CONFIRM, 2, "确认送达", false));
        assertTrue(ex.getMessage().contains("送达照片"));
    }

    @Test
    @DisplayName("确认送达：skipPhoto 跳过照片校验后可流转并通知发布者")
    void arrivalWithSkipPhoto() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.DELIVERING, 1, 2));
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        service.updateOrderStatus(10, OrderStatus.WAIT_CONFIRM, 2, "确认送达", true);

        verify(historyService).saveHistoryRecord(any());
        verify(notificationService).send(eq(1), eq(10), anyString());
    }

    @Test
    @DisplayName("并发防护：确认收货时 updateById 返回 0（已被定时任务抢先）→ 抛异常且不结算")
    void confirmReceiptLosesRaceNoSettlement() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.WAIT_CONFIRM, 1, 2));
        when(orderMapper.updateById(any(Order.class))).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> service.updateOrderStatus(10, OrderStatus.COMPLETED, 1, "确认收货", false));
        verify(walletService, never()).settleOrderReward(any(), any(), any(), any());
    }

    @Test
    @DisplayName("确认收货成功：触发报酬结算并通知接单者")
    void confirmReceiptSettlesReward() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.WAIT_CONFIRM, 1, 2));
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        service.updateOrderStatus(10, OrderStatus.COMPLETED, 1, "确认收货", false);

        verify(walletService).settleOrderReward(eq(1), eq(2), eq(10), eq(new BigDecimal("5.00")));
        verify(notificationService).send(eq(2), eq(10), anyString());
    }

    // ---------------- confirmArrival：接单者确认送达 ----------------

    @Test
    @DisplayName("确认送达：非配送中状态拒绝")
    void confirmArrivalRejectsWrongStatus() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        assertThrows(BusinessException.class, () -> service.confirmArrival(10, true));
    }

    @Test
    @DisplayName("确认送达：非接单者本人（403）")
    void confirmArrivalOnlyTaker() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.DELIVERING, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(3, "路人", 100, false));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.confirmArrival(10, true));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("确认送达：缺送达照片且未跳过 → 拒绝")
    void confirmArrivalRejectsMissingPhoto() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.DELIVERING, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.confirmArrival(10, false));
        assertTrue(ex.getMessage().contains("送达照片"));
    }

    @Test
    @DisplayName("确认送达成功：记录送达时间、写历史并通知发布者")
    void confirmArrivalSuccess() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.DELIVERING, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        service.confirmArrival(10, true);

        verify(historyService).saveHistoryRecord(any());
        verify(notificationService).send(eq(1), eq(10), anyString());
    }

    @Test
    @DisplayName("确认送达：updateById 被调用一次（记录 confirmArrivalTime）")
    void confirmArrivalWritesConfirmTime() {
        Order o = order(10, OrderStatus.DELIVERING, 1, 2);
        when(orderMapper.selectById(10)).thenReturn(o);
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        service.confirmArrival(10, true);

        verify(orderMapper).updateById(any(Order.class));
        assertEquals(OrderStatus.WAIT_CONFIRM, o.getStatus());
    }
}
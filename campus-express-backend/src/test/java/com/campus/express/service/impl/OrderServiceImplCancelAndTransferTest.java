package com.campus.express.service.impl;

import com.campus.express.common.BusinessException;
import com.campus.express.common.OrderStatus;
import com.campus.express.common.TransferStatus;
import com.campus.express.entity.Order;
import com.campus.express.entity.OrderTransfer;
import com.campus.express.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
 * 取消订单（R-11 / R-12）与转单接力（R-24）单元测试
 * 覆盖 ② 重复解冻防护（待接单取消的状态守卫）、⑧ 接力接单状态与时间校验
 */
class OrderServiceImplCancelAndTransferTest extends AbstractOrderServiceTest {

    private static final String VALID_REASON = "其他原因";
    private static final String INVALID_REASON = "随便写的理由";

    // ---------------- cancelOrder：待接单（发布者取消） ----------------

    @Test
    @DisplayName("取消-待接单：非发布者（403）")
    void cancelWaitAcceptOnlyPublisher() {
        when(userService.getCurrentUser()).thenReturn(user(9, "路人", 100, false));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.WAIT_ACCEPT, 1, null));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancelOrder(10, VALID_REASON));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("取消-待接单：原因不在发布者清单 → 拒绝")
    void cancelWaitAcceptInvalidReason() {
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.WAIT_ACCEPT, 1, null));
        assertThrows(BusinessException.class, () -> service.cancelOrder(10, INVALID_REASON));
    }

    @Test
    @DisplayName("取消-待接单：状态守卫影响行数为 0（已被抢单/被定时任务取消）→ 拒绝，避免重复解冻")
    void cancelWaitAcceptLosesRace() {
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.WAIT_ACCEPT, 1, null));
        when(orderMapper.update(any(), any())).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.cancelOrder(10, VALID_REASON));
        verify(walletService, never()).unfreezeBalance(any(), any(), any());
    }

    @Test
    @DisplayName("取消-待接单成功：解冻报酬并写历史")
    void cancelWaitAcceptSuccess() {
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.WAIT_ACCEPT, 1, null));
        when(orderMapper.update(any(), any())).thenReturn(1);

        service.cancelOrder(10, VALID_REASON);

        verify(walletService).unfreezeBalance(eq(1), eq(10), any());
        verify(historyService).saveHistoryRecord(any());
    }

    // ---------------- cancelOrder：已接单/取件中（接单者取消） ----------------

    @Test
    @DisplayName("取消-已接单：非接单者（403）")
    void cancelAcceptedOnlyTaker() {
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancelOrder(10, VALID_REASON));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("取消-已接单：接力中不允许直接取消")
    void cancelAcceptedRejectedWhileRelaying() {
        Order o = order(10, OrderStatus.ACCEPTED, 1, 2);
        o.setTransferStatus(TransferStatus.PENDING);
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(o);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancelOrder(10, VALID_REASON));
        assertTrue(ex.getMessage().contains("转单接力"));
    }

    @Test
    @DisplayName("取消-已接单：原因不在接单者清单 → 拒绝")
    void cancelAcceptedInvalidReason() {
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        assertThrows(BusinessException.class, () -> service.cancelOrder(10, INVALID_REASON));
    }

    @Test
    @DisplayName("取消-已接单成功：扣信誉分5、退回待接单、通知发布者")
    void cancelAcceptedSuccess() {
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        when(orderMapper.update(any(), any())).thenReturn(1);

        service.cancelOrder(10, VALID_REASON);

        verify(userService).updateCreditScore(eq(2), eq(-5), anyString(), anyString(), eq(10));
        verify(historyService).saveHistoryRecord(any());
        verify(notificationService).send(eq(1), eq(10), anyString());
    }

    @Test
    @DisplayName("取消：配送中及之后禁止取消")
    void cancelRejectedForDelivering() {
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.DELIVERING, 1, 2));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancelOrder(10, VALID_REASON));
        assertTrue(ex.getMessage().contains("不允许取消"));
    }

    // ---------------- initiateTransfer：发起转单接力 ----------------

    @Test
    @DisplayName("发起转单：非接单者（403）")
    void initiateTransferOnlyTaker() {
        when(userService.getCurrentUser()).thenReturn(user(3, "路人", 100, false));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.initiateTransfer(10, VALID_REASON));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("发起转单：信誉分低于 90 → 无权限")
    void initiateTransferRejectsLowCredit() {
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 85, false));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.initiateTransfer(10, VALID_REASON));
        assertTrue(ex.getMessage().contains("信誉分低于"));
    }

    @Test
    @DisplayName("发起转单：本周次数达上限 3 → 拒绝")
    void initiateTransferRejectsWeeklyLimit() {
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        when(transferMapper.selectCount(any())).thenReturn(3L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.initiateTransfer(10, VALID_REASON));
        assertTrue(ex.getMessage().contains("上限"));
    }

    @Test
    @DisplayName("发起转单：配送中（包裹已在手）不可发起")
    void initiateTransferRejectsDelivering() {
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.DELIVERING, 1, 2));
        when(transferMapper.selectCount(any())).thenReturn(0L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.initiateTransfer(10, VALID_REASON));
        assertTrue(ex.getMessage().contains("不可发起转单"));
    }

    @Test
    @DisplayName("发起转单：已在接力中不可重复发起")
    void initiateTransferRejectsDuplicate() {
        Order o = order(10, OrderStatus.ACCEPTED, 1, 2);
        o.setTransferStatus(TransferStatus.PENDING);
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(o);
        when(transferMapper.selectCount(any())).thenReturn(0L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.initiateTransfer(10, VALID_REASON));
        assertTrue(ex.getMessage().contains("已在转单接力中"));
    }

    @Test
    @DisplayName("发起转单：原因非法 → 拒绝")
    void initiateTransferRejectsInvalidReason() {
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        when(transferMapper.selectCount(any())).thenReturn(0L);
        assertThrows(BusinessException.class, () -> service.initiateTransfer(10, INVALID_REASON));
    }

    @Test
    @DisplayName("发起转单成功：写接力流水、历史、通知发布者并广播")
    void initiateTransferSuccess() {
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        when(transferMapper.selectCount(any())).thenReturn(0L);
        when(orderMapper.update(any(), any())).thenReturn(1);

        service.initiateTransfer(10, VALID_REASON);

        // 每周次数是「先查后插」，必须先锁定用户行，避免并发突破上限
        verify(userService).lockForUpdate(2);
        verify(transferMapper).insert(any(OrderTransfer.class));
        verify(historyService).saveHistoryRecord(any());
        verify(notificationService).send(eq(1), eq(10), anyString());
        verify(webSocketSessionManager).broadcast("new_relay");
    }

    // ---------------- cancelTransfer：撤回转单接力 ----------------

    @Test
    @DisplayName("发起转单：并发下状态守卫失败（订单已被取消/推进）→ 拒绝且不写接力流水")
    void initiateTransferLosesRace() {
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        when(transferMapper.selectCount(any())).thenReturn(0L);
        when(orderMapper.update(any(), any())).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.initiateTransfer(10, VALID_REASON));

        verify(transferMapper, never()).insert(any(OrderTransfer.class));
        verify(historyService, never()).saveHistoryRecord(any());
        verify(notificationService, never()).send(any(), any(), anyString());
    }

    @Test
    @DisplayName("撤回转单：非接单者（403）")
    void cancelTransferOnlyTaker() {
        when(userService.getCurrentUser()).thenReturn(user(3, "路人", 100, false));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancelTransfer(10));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("撤回转单：当前未处于接力中 → 拒绝")
    void cancelTransferRejectsNotPending() {
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancelTransfer(10));
        assertTrue(ex.getMessage().contains("未处于转单接力中"));
    }

    @Test
    @DisplayName("撤回转单成功：关闭流水、通知发布者并广播")
    void cancelTransferSuccess() {
        Order o = order(10, OrderStatus.ACCEPTED, 1, 2);
        o.setTransferStatus(TransferStatus.PENDING);
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(o);
        when(orderMapper.update(any(), any())).thenReturn(1);

        service.cancelTransfer(10);

        verify(transferMapper).update(any(), any());
        verify(historyService).saveHistoryRecord(any());
        verify(webSocketSessionManager).broadcast("new_relay");
    }

    // ---------------- relayTakeOrder：接力接单（⑧） ----------------

    @Test
    @DisplayName("接力：发布者角色不可接力（403）")
    void relayRejectsPublisherRole() {
        when(userService.getCurrentUser()).thenReturn(user(2, "发布者", 100, true));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.relayTakeOrder(10));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("接力：订单不存在")
    void relayNotFound() {
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.relayTakeOrder(10));
    }

    @Test
    @DisplayName("接力：订单不在接力中 → 拒绝")
    void relayRejectsNotPending() {
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 3));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.relayTakeOrder(10));
        assertTrue(ex.getMessage().contains("不在转单接力中"));
    }

    @Test
    @DisplayName("接力：配送中（包裹已在手）不可接力")
    void relayRejectsDelivering() {
        Order o = order(10, OrderStatus.DELIVERING, 1, 3);
        o.setTransferStatus(TransferStatus.PENDING);
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(o);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.relayTakeOrder(10));
        assertTrue(ex.getMessage().contains("不可接力承接"));
    }

    @Test
    @DisplayName("接力：期望取件时间已过不可接力")
    void relayRejectsExpiredExpectTime() {
        Order o = order(10, OrderStatus.ACCEPTED, 1, 3);
        o.setTransferStatus(TransferStatus.PENDING);
        o.setExpectTime(LocalDateTime.now().minusMinutes(1));
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(o);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.relayTakeOrder(10));
        assertTrue(ex.getMessage().contains("期望取件时间已过"));
    }

    @Test
    @DisplayName("接力：不能接力自己发起的转单")
    void relayRejectsOwnTransfer() {
        Order o = order(10, OrderStatus.ACCEPTED, 1, 2);
        o.setTransferStatus(TransferStatus.PENDING);
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(o);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.relayTakeOrder(10));
        assertTrue(ex.getMessage().contains("自己发起"));
    }

    @Test
    @DisplayName("接力：不能接力自己发布的订单")
    void relayRejectsOwnPublishedOrder() {
        Order o = order(10, OrderStatus.ACCEPTED, 2, 3);
        o.setTransferStatus(TransferStatus.PENDING);
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(o);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.relayTakeOrder(10));
        assertTrue(ex.getMessage().contains("自己发布"));
    }

    @Test
    @DisplayName("接力：信誉分低于阈值 → 拒绝")
    void relayRejectsLowCredit() {
        Order o = order(10, OrderStatus.ACCEPTED, 1, 3);
        o.setTransferStatus(TransferStatus.PENDING);
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 70, false));
        when(orderMapper.selectById(10)).thenReturn(o);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.relayTakeOrder(10));
        assertTrue(ex.getMessage().contains("信誉分"));
    }

    @Test
    @DisplayName("接力：进行中订单达上限 → 拒绝")
    void relayRejectsConcurrentLimit() {
        Order o = order(10, OrderStatus.ACCEPTED, 1, 3);
        o.setTransferStatus(TransferStatus.PENDING);
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(o);
        when(orderMapper.selectCount(any())).thenReturn(3L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.relayTakeOrder(10));
        assertTrue(ex.getMessage().contains("上限"));
    }

    @Test
    @DisplayName("接力：乐观锁影响行数为 0 → 已被他人接力")
    void relayLosesRace() {
        Order o = order(10, OrderStatus.ACCEPTED, 1, 3);
        o.setTransferStatus(TransferStatus.PENDING);
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(o);
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(orderMapper.relayTakeOrder(any(), any(), any(), any())).thenReturn(0);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.relayTakeOrder(10));
        assertTrue(ex.getMessage().contains("已被他人接力"));
    }

    @Test
    @DisplayName("接力成功：换人、关闭接力流水、通知双方并广播")
    void relaySuccess() {
        Order o = order(10, OrderStatus.ACCEPTED, 1, 3);
        o.setTransferStatus(TransferStatus.PENDING);
        when(userService.getCurrentUser()).thenReturn(user(2, "接力者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(o);
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(orderMapper.relayTakeOrder(any(), any(), any(), any())).thenReturn(1);
        when(userService.getById(3)).thenReturn(user(3, "原接单者", 100, false));

        service.relayTakeOrder(10);

        verify(transferMapper).update(any(), any());
        verify(historyService).saveHistoryRecord(any());
        verify(notificationService).send(eq(3), eq(10), anyString());
        verify(notificationService).send(eq(1), eq(10), anyString());
        verify(webSocketSessionManager).broadcast("new_relay");
    }

    @Test
    @DisplayName("取消-已接单：并发下状态守卫失败（已被定时任务处理）→ 不扣信誉分、不写历史")
    void cancelAcceptedLosesRace() {
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        when(orderMapper.update(any(), any())).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.cancelOrder(10, VALID_REASON));

        verify(userService, never()).updateCreditScore(any(), anyInt(), anyString(), anyString(), any());
        verify(historyService, never()).saveHistoryRecord(any());
        verify(walletService, never()).unfreezeBalance(any(), any(), any());
    }
}
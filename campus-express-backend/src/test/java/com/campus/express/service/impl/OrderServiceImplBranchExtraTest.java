package com.campus.express.service.impl;

import com.campus.express.common.BusinessException;
import com.campus.express.common.OrderStatus;
import com.campus.express.common.TransferStatus;
import com.campus.express.dto.OrderUpdateDTO;
import com.campus.express.entity.Order;
import com.campus.express.vo.OrderDetailVO;
import com.campus.express.vo.OrderVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderServiceImpl 稀疏分支补充测试
 *
 * <p>补齐剩余低概率分支：照片落盘 IO 失败、解冻失败不阻塞主流程、修改待接单报酬不变时
 * 不做资金调整、订单详情以接单者身份参与、接力时原接单者查不到的用户兜底、退回待接单时
 * 取件时间已过的追加提示、抢单时期望取件时间为空。</p>
 */
class OrderServiceImplBranchExtraTest extends AbstractOrderServiceTest {

    // ---------------- 照片落盘失败 ----------------

    @Test
    @DisplayName("上传照片：落盘 IO 失败 → 提示保存失败")
    void uploadPhotoTransferFailure() throws Exception {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        MultipartFile f = mock(MultipartFile.class);
        when(f.isEmpty()).thenReturn(false);
        when(f.getOriginalFilename()).thenReturn("a.jpg");
        when(f.getInputStream()).thenReturn(new ByteArrayInputStream(jpegHeader()));
        when(f.getSize()).thenReturn(1024L);
        doThrow(new IOException("disk full")).when(f).transferTo(any(File.class));

        BusinessException ex = assertThrowsBusiness(() -> service.uploadOrderPhoto(10, "pickup", f));
        assertEquals(true, ex.getMessage().contains("保存失败"));
    }

    // ---------------- 修改待接单：报酬不变 ----------------

    @Test
    @DisplayName("修改待接单：报酬不变 → 不校验支付密码、不调整冻结")
    void updateWaitRewardUnchanged() {
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.WAIT_ACCEPT, 1, null));
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        service.updateWaitOrder(updateDto(10, new BigDecimal("5.00")));

        verify(userService, never()).verifyPayPassword(any(), any());
        verify(walletService, never()).adjustFrozenBalance(any(), any(), any());
        verify(orderMapper).updateById(any(Order.class));
    }

    // ---------------- 订单详情：接单者身份参与 ----------------

    @Test
    @DisplayName("订单详情：接单者本人查看 → 视为参与者，保留取件码")
    void detailTakerIsParticipant() {
        OrderVO vo = new OrderVO();
        vo.setOrderId(10);
        vo.setStatus(OrderStatus.DELIVERING);
        vo.setPublisherId(1);
        vo.setTakerId(2);
        vo.setPickupCode("1234");
        when(orderMapper.selectOrderVOById(10)).thenReturn(vo);
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(historyService.listByOrderId(10)).thenReturn(List.of());
        when(evaluationMapper.selectByOrderId(10)).thenReturn(List.of());

        OrderDetailVO detail = service.getOrderVOById(10);

        assertEquals("1234", detail.getOrder().getPickupCode());
    }

    // ---------------- 接力：原接单者查不到的兜底 ----------------

    @Test
    @DisplayName("接力成功：原接单者用户信息缺失 → 历史记录以「用户#id」兜底，不抛异常")
    void relayToleratesMissingFromUser() {
        Order o = order(10, OrderStatus.ACCEPTED, 1, 3);
        o.setTransferStatus(TransferStatus.PENDING);
        when(userService.getCurrentUser()).thenReturn(user(2, "接力者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(o);
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(orderMapper.relayTakeOrder(any(), any(), any(), any())).thenReturn(1);
        when(userService.getById(3)).thenReturn(null);

        assertDoesNotThrow(() -> service.relayTakeOrder(10));

        verify(historyService).saveHistoryRecord(any());
    }

    // ---------------- 退回待接单：取件时间已过的追加提示 ----------------

    @Test
    @DisplayName("未取件超时：期望取件时间已过 → 通知追加「暂不展示且无法被接单」提示")
    void stuckOrderReopenNoticeWhenExpectTimePast() {
        Order o = order(10, OrderStatus.ACCEPTED, 1, 2);
        o.setExpectTime(LocalDateTime.now().minusHours(1));
        when(orderMapper.update(any(), any())).thenReturn(1);

        ReflectionTestUtils.invokeMethod(timeout, "handleStuckAcceptedOrder", o);

        verify(notificationService).send(eq(1), eq(10), contains("期望取件时间已过"));
    }

    // ---------------- 解冻失败不阻塞主流程 ----------------

    @Test
    @DisplayName("过期未接单：解冻失败（冻结不足）→ 记录告警但订单照常取消")
    void cancelExpiredToleratesUnfreezeFailure() {
        Order o = order(10, OrderStatus.WAIT_ACCEPT, 1, null);
        when(orderMapper.selectList(any())).thenReturn(List.of(o));
        when(orderMapper.update(any(), any())).thenReturn(1);
        doThrow(new BusinessException("冻结余额不足")).when(walletService).unfreezeBalance(any(), any(), any());

        Integer n = ReflectionTestUtils.invokeMethod(timeout, "cancelExpiredWaitOrders", LocalDateTime.now());

        assertEquals(1, n);
    }

    @Test
    @DisplayName("未送达超时：解冻失败 → 记录告警但不中断扣分与通知")
    void overdueToleratesUnfreezeFailure() {
        Order o = order(10, OrderStatus.PICKING, 1, 2);
        when(orderMapper.update(any(), any())).thenReturn(1);
        doThrow(new BusinessException("冻结余额不足")).when(walletService).unfreezeBalance(any(), any(), any());

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(timeout, "handleOverdueDeliveryOrder", o));

        verify(userService).updateCreditScore(eq(2), eq(-5), any(), any(), eq(10));
        verify(notificationService).send(eq(1), eq(10), any());
    }

    // ---------------- 抢单：期望取件时间为空 ----------------

    @Test
    @DisplayName("抢单：期望取件时间为空（历史订单）→ 不因过期校验拦截，正常抢单")
    void takeOrderToleratesNullExpectTime() {
        Order o = order(10, OrderStatus.WAIT_ACCEPT, 1, null);
        o.setExpectTime(null);
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(o);
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(orderMapper.optimisticTakeOrder(any(), any(), any())).thenReturn(1);

        service.takeOrder(10);

        verify(historyService).saveHistoryRecord(any());
    }

    // ---------------- 构造辅助 ----------------

    private BusinessException assertThrowsBusiness(org.junit.jupiter.api.function.Executable executable) {
        return org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class, executable);
    }

    private OrderUpdateDTO updateDto(int orderId, BigDecimal reward) {
        OrderUpdateDTO dto = new OrderUpdateDTO();
        dto.setOrderId(orderId);
        dto.setPickupCode("1234");
        dto.setExpressCompany("顺丰");
        dto.setPickupLocation("菜鸟驿站A");
        dto.setDeliverLocation("1号楼");
        dto.setExpectTime(LocalDateTime.now().plusHours(2));
        dto.setRewardAmount(reward);
        dto.setPayPassword("123456");
        return dto;
    }

    private byte[] jpegHeader() {
        return new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
        };
    }
}
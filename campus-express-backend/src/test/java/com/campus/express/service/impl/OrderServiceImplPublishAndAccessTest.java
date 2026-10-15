package com.campus.express.service.impl;

import com.campus.express.common.BusinessException;
import com.campus.express.common.OrderStatus;
import com.campus.express.dto.OrderPublishDTO;
import com.campus.express.dto.OrderUpdateDTO;
import com.campus.express.entity.Order;
import com.campus.express.vo.OrderDetailVO;
import com.campus.express.vo.OrderReuseVO;
import com.campus.express.vo.OrderVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发布 / 修改待接单订单 / 订单详情越权 / 照片上传 / 催单 单元测试
 * 覆盖 ⑦ 修改订单 updateById 返回值检查、⑥ 订单详情非参与者脱敏
 */
class OrderServiceImplPublishAndAccessTest extends AbstractOrderServiceTest {

    private OrderPublishDTO publishDto(BigDecimal reward, LocalDateTime expectTime) {
        OrderPublishDTO dto = new OrderPublishDTO();
        dto.setPickupCode("1234");
        dto.setExpressCompany("顺丰");
        dto.setPickupLocation("菜鸟驿站A");
        dto.setDeliverLocation("1号楼");
        dto.setExpectTime(expectTime);
        dto.setRewardAmount(reward);
        dto.setPayPassword("123456");
        return dto;
    }

    private OrderUpdateDTO updateDto(int orderId, BigDecimal reward, LocalDateTime expectTime) {
        OrderUpdateDTO dto = new OrderUpdateDTO();
        dto.setOrderId(orderId);
        dto.setPickupCode("1234");
        dto.setExpressCompany("顺丰");
        dto.setPickupLocation("菜鸟驿站A");
        dto.setDeliverLocation("1号楼");
        dto.setExpectTime(expectTime);
        dto.setRewardAmount(reward);
        dto.setPayPassword("123456");
        return dto;
    }

    // ---------------- publishOrder ----------------

    @Test
    @DisplayName("发布：报酬低于下限 2 元 → 拒绝")
    void publishRejectsRewardBelowMin() {
        assertThrows(BusinessException.class,
                () -> service.publishOrder(publishDto(new BigDecimal("1.00"), LocalDateTime.now().plusHours(2))));
    }

    @Test
    @DisplayName("发布：报酬高于上限 50 元 → 拒绝")
    void publishRejectsRewardAboveMax() {
        assertThrows(BusinessException.class,
                () -> service.publishOrder(publishDto(new BigDecimal("51.00"), LocalDateTime.now().plusHours(2))));
    }

    @Test
    @DisplayName("发布：期望取件时间早于当前时间 → 拒绝")
    void publishRejectsPastExpectTime() {
        assertThrows(BusinessException.class,
                () -> service.publishOrder(publishDto(new BigDecimal("5.00"), LocalDateTime.now().minusMinutes(1))));
    }

    @Test
    @DisplayName("发布：接单者角色（canPublish=false）无发布权限（403）")
    void publishRejectsNonPublisher() {
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.publishOrder(publishDto(new BigDecimal("5.00"), LocalDateTime.now().plusHours(2))));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("发布：当日取消达上限 → 禁止发布")
    void publishRejectsDailyCancelLimit() {
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));
        when(historyService.countPublisherCancelSince(any(), any())).thenReturn(3L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.publishOrder(publishDto(new BigDecimal("5.00"), LocalDateTime.now().plusHours(2))));
        assertTrue(ex.getMessage().contains("上限"));
    }

    @Test
    @DisplayName("发布成功：冻结报酬、写历史、广播新订单")
    void publishSuccess() {
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));
        when(historyService.countPublisherCancelSince(any(), any())).thenReturn(0L);
        doAnswer(inv -> {
            ((Order) inv.getArgument(0)).setOrderId(100);
            return 1;
        }).when(orderMapper).insert(any(Order.class));

        Integer orderId = service.publishOrder(publishDto(new BigDecimal("5.00"), LocalDateTime.now().plusHours(2)));

        assertEquals(100, orderId);
        verify(userService).verifyPayPassword(eq(1), anyString());
        verify(walletService).freezeBalance(eq(1), eq(100), eq(new BigDecimal("5.00")));
        verify(historyService).saveHistoryRecord(any());
        verify(webSocketSessionManager).broadcast("new_order");
    }

    @Test
    @DisplayName("发布：并发下预检同时通过、数据库唯一约束拦截 → 转成友好业务异常")
    void publishTranslatesDbDuplicateKey() {
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));
        when(historyService.countPublisherCancelSince(any(), any())).thenReturn(0L);
        when(orderMapper.insert(any(Order.class))).thenThrow(new DuplicateKeyException("uk_active_pickup"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.publishOrder(publishDto(new BigDecimal("5.00"), LocalDateTime.now().plusHours(2))));

        assertTrue(ex.getMessage().contains("相同取件码"), "数据库撞码应转成用户可读的提示");
    }

    // ---------------- updateWaitOrder（⑦） ----------------

    @Test
    @DisplayName("修改待接单：非发布者（403）")
    void updateWaitOrderOnlyPublisher() {
        when(userService.getCurrentUser()).thenReturn(user(2, "路人", 100, false));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.WAIT_ACCEPT, 1, null));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateWaitOrder(updateDto(10, new BigDecimal("5.00"), LocalDateTime.now().plusHours(2))));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("修改待接单：订单已被接单 → 拒绝")
    void updateWaitOrderRejectsAccepted() {
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        assertThrows(BusinessException.class,
                () -> service.updateWaitOrder(updateDto(10, new BigDecimal("5.00"), LocalDateTime.now().plusHours(2))));
    }

    @Test
    @DisplayName("修改待接单：提高报酬需校验支付密码并追加冻结")
    void updateWaitOrderRaiseReward() {
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.WAIT_ACCEPT, 1, null));
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        service.updateWaitOrder(updateDto(10, new BigDecimal("8.00"), LocalDateTime.now().plusHours(2)));

        verify(userService).verifyPayPassword(eq(1), anyString());
        verify(walletService).adjustFrozenBalance(eq(1), eq(10), eq(new BigDecimal("3.00")));
    }

    @Test
    @DisplayName("修改待接单：降低报酬无需支付密码，解冻差额")
    void updateWaitOrderLowerReward() {
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.WAIT_ACCEPT, 1, null));
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        service.updateWaitOrder(updateDto(10, new BigDecimal("3.00"), LocalDateTime.now().plusHours(2)));

        verify(walletService).adjustFrozenBalance(eq(1), eq(10), eq(new BigDecimal("-2.00")));
    }

    @Test
    @DisplayName("修改待接单：updateById 返回 0（订单被并发抢走）→ 抛异常（防止冻结额与报酬不一致）")
    void updateWaitOrderLosesRace() {
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.WAIT_ACCEPT, 1, null));
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(orderMapper.updateById(any(Order.class))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateWaitOrder(updateDto(10, new BigDecimal("8.00"), LocalDateTime.now().plusHours(2))));
        assertTrue(ex.getMessage().contains("已被接单或已被修改"));
    }

    @Test
    @DisplayName("修改待接单：改码撞车被数据库唯一约束拦截 → 转成友好业务异常")
    void updateWaitOrderTranslatesDbDuplicateKey() {
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.WAIT_ACCEPT, 1, null));
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(orderMapper.updateById(any(Order.class))).thenThrow(new DuplicateKeyException("uk_active_pickup"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateWaitOrder(updateDto(10, new BigDecimal("5.00"), LocalDateTime.now().plusHours(2))));

        assertTrue(ex.getMessage().contains("相同取件码"), "数据库撞码应转成用户可读的提示");
    }

    // ---------------- getOrderVOById：越权防护与脱敏（⑥） ----------------

    @Test
    @DisplayName("订单详情：订单不存在")
    void detailNotFound() {
        when(orderMapper.selectOrderVOById(10)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getOrderVOById(10));
    }

    @Test
    @DisplayName("订单详情：非参与者查看进行中订单 → 403")
    void detailRejectsNonParticipant() {
        OrderVO vo = voOf(OrderStatus.DELIVERING, 1, 2);
        when(orderMapper.selectOrderVOById(10)).thenReturn(vo);
        when(userService.getCurrentUser()).thenReturn(user(3, "路人", 100, false));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getOrderVOById(10));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("订单详情：非参与者预览待接单订单 → 取件码/照片脱敏")
    void detailMasksSensitiveFieldsForOutsider() {
        OrderVO vo = voOf(OrderStatus.WAIT_ACCEPT, 1, null);
        vo.setPickupPhoto("/uploads/order/a.jpg");
        vo.setDeliverPhoto("/uploads/order/b.jpg");
        when(orderMapper.selectOrderVOById(10)).thenReturn(vo);
        when(userService.getCurrentUser()).thenReturn(user(3, "路人", 100, false));
        when(historyService.listByOrderId(10)).thenReturn(List.of());
        when(evaluationMapper.selectByOrderId(10)).thenReturn(List.of());

        OrderDetailVO detail = service.getOrderVOById(10);

        assertNull(detail.getOrder().getPickupCode());
        assertNull(detail.getOrder().getPickupPhoto());
        assertNull(detail.getOrder().getDeliverPhoto());
    }

    @Test
    @DisplayName("订单详情：参与者可查看完整信息（保留取件码）")
    void detailFullForParticipant() {
        OrderVO vo = voOf(OrderStatus.DELIVERING, 1, 2);
        when(orderMapper.selectOrderVOById(10)).thenReturn(vo);
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));
        when(historyService.listByOrderId(10)).thenReturn(List.of());
        when(evaluationMapper.selectByOrderId(10)).thenReturn(List.of());

        OrderDetailVO detail = service.getOrderVOById(10);

        assertEquals("1234", detail.getOrder().getPickupCode());
    }

    // ---------------- uploadOrderPhoto：上传守卫 ----------------

    @Test
    @DisplayName("上传照片：非接单者（403）")
    void uploadPhotoOnlyTaker() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(3, "路人", 100, false));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.uploadOrderPhoto(10, "pickup", new MockMultipartFile("f", "a.jpg", null, new byte[]{1})));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("上传照片：类型非法 → 拒绝")
    void uploadPhotoRejectsInvalidType() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.uploadOrderPhoto(10, "other", new MockMultipartFile("f", "a.jpg", null, new byte[]{1})));
        assertTrue(ex.getMessage().contains("pickup"));
    }

    @Test
    @DisplayName("上传照片：取件照仅在已接单/取件中可传（配送中被拒）")
    void uploadPhotoRejectsWrongStatus() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.DELIVERING, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.uploadOrderPhoto(10, "pickup", new MockMultipartFile("f", "a.jpg", null, new byte[]{1})));
        assertTrue(ex.getMessage().contains("不可上传取件照片"));
    }

    // ---------------- urgeTaker：催单 ----------------

    @Test
    @DisplayName("催单：非发布者（403）")
    void urgeOnlyPublisher() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.urgeTaker(10));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("催单：待接单状态无需催单 → 拒绝")
    void urgeRejectsWaitAccept() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.WAIT_ACCEPT, 1, null));
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));
        assertThrows(BusinessException.class, () -> service.urgeTaker(10));
    }

    @Test
    @DisplayName("催单：10 分钟内已提醒过 → 拒绝防刷")
    void urgeRejectsRecently() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));
        when(notificationService.recentlyUrged(2, 10)).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.urgeTaker(10));
        assertTrue(ex.getMessage().contains("已提醒"));
    }

    @Test
    @DisplayName("催单成功：向接单者发送提醒")
    void urgeSuccess() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));
        when(notificationService.recentlyUrged(2, 10)).thenReturn(false);

        service.urgeTaker(10);

        verify(notificationService).send(eq(2), eq(10), anyString());
    }

    // ---------------- getReuseTemplate ----------------

    @Test
    @DisplayName("再次发布模板：非发布者本人（403）")
    void reuseTemplateOnlyPublisher() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.COMPLETED, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getReuseTemplate(10));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("再次发布模板：返回包裹信息，报酬沿用原值")
    void reuseTemplateSuccess() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.COMPLETED, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));

        OrderReuseVO vo = service.getReuseTemplate(10);

        assertEquals("1234", vo.getPickupCode());
        assertEquals(new BigDecimal("5.00"), vo.getRewardAmount());
    }

    private OrderVO voOf(String status, int publisherId, Integer takerId) {
        OrderVO vo = new OrderVO();
        vo.setOrderId(10);
        vo.setStatus(status);
        vo.setPublisherId(publisherId);
        vo.setTakerId(takerId);
        vo.setPickupCode("1234");
        vo.setRewardAmount(new BigDecimal("5.00"));
        vo.setVersion(0);
        return vo;
    }
}
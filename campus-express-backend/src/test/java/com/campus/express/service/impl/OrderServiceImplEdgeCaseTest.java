package com.campus.express.service.impl;

import com.campus.express.common.BusinessException;
import com.campus.express.common.OrderStatus;
import com.campus.express.dto.OrderPublishDTO;
import com.campus.express.entity.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderServiceImpl 边界分支单元测试
 *
 * <p>补齐主流程之外的稀疏分支：各方法「订单不存在」早退、状态机正向推进（取件中→配送中、
 * 配送中→待收货带照片）与普通状态更新的通知分支、发布时取件码为空/重复、再次发布模板与
 * 催单的空单守卫。</p>
 */
class OrderServiceImplEdgeCaseTest extends AbstractOrderServiceTest {

    // ---------------- 状态流转：缺失与普通分支 ----------------

    @Test
    @DisplayName("状态流转：订单不存在 → 拒绝")
    void statusRejectsMissingOrder() {
        when(orderMapper.selectById(10)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateOrderStatus(10, OrderStatus.PICKING, 2, "推进", false));
        assertTrue(ex.getMessage().contains("订单不存在"));
    }

    @Test
    @DisplayName("状态流转：已接单→取件中，通知发布者状态更新为目标状态")
    void statusAdvanceNotifiesPublisher() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        service.updateOrderStatus(10, OrderStatus.PICKING, 2, "开始取件", false);

        verify(notificationService).send(eq(1), eq(10), contains("取件中"));
    }

    @Test
    @DisplayName("状态流转：取件中→配送中（接单者推进）")
    void statusPickingToDelivering() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.PICKING, 1, 2));
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        service.updateOrderStatus(10, OrderStatus.DELIVERING, 2, "配送中", false);

        verify(historyService).saveHistoryRecord(any());
        verify(notificationService).send(eq(1), eq(10), contains("配送中"));
    }

    @Test
    @DisplayName("状态流转：配送中→待收货且已上传送达照片 → 无需跳过即可流转")
    void statusDeliverToWaitConfirmWithPhoto() {
        Order o = order(10, OrderStatus.DELIVERING, 1, 2);
        o.setDeliverPhoto("/uploads/order/x.jpg");
        when(orderMapper.selectById(10)).thenReturn(o);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        service.updateOrderStatus(10, OrderStatus.WAIT_CONFIRM, 2, "确认送达", false);

        verify(notificationService).send(eq(1), eq(10), contains("确认收货"));
    }

    // ---------------- 各方法「订单不存在」早退 ----------------

    @Test
    @DisplayName("确认送达：订单不存在 → 拒绝")
    void confirmArrivalMissingOrder() {
        when(orderMapper.selectById(10)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.confirmArrival(10, true));
        assertTrue(ex.getMessage().contains("订单不存在"));
    }

    @Test
    @DisplayName("再次发布模板：订单不存在 → 拒绝")
    void reuseTemplateMissingOrder() {
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));
        when(orderMapper.selectById(10)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getReuseTemplate(10));
        assertTrue(ex.getMessage().contains("订单不存在"));
    }

    @Test
    @DisplayName("取消订单：订单不存在 → 拒绝")
    void cancelMissingOrder() {
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));
        when(orderMapper.selectById(10)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancelOrder(10, "其他原因"));
        assertTrue(ex.getMessage().contains("订单不存在"));
    }

    @Test
    @DisplayName("催单：订单不存在 → 拒绝")
    void urgeMissingOrder() {
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));
        when(orderMapper.selectById(10)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.urgeTaker(10));
        assertTrue(ex.getMessage().contains("订单不存在"));
    }

    // ---------------- 发布：取件码校验 ----------------

    @Test
    @DisplayName("发布：取件码为空 → 拒绝")
    void publishRejectsBlankPickupCode() {
        OrderPublishDTO dto = publishDto();
        dto.setPickupCode("   ");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.publishOrder(dto));
        assertTrue(ex.getMessage().contains("取件码不能为空"));
    }

    @Test
    @DisplayName("发布：同取件地点下已有相同取件码的进行中订单 → 拒绝")
    void publishRejectsDuplicatePickupCode() {
        when(orderMapper.selectCount(any())).thenReturn(1L);
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.publishOrder(publishDto()));
        assertTrue(ex.getMessage().contains("请勿重复填写"));
    }

    // ---------------- 构造辅助 ----------------

    private OrderPublishDTO publishDto() {
        OrderPublishDTO dto = new OrderPublishDTO();
        dto.setPickupCode("1234");
        dto.setExpressCompany("顺丰");
        dto.setPickupLocation("菜鸟驿站A");
        dto.setDeliverLocation("1号楼");
        dto.setExpectTime(LocalDateTime.now().plusHours(2));
        dto.setRewardAmount(new BigDecimal("5.00"));
        dto.setPayPassword("123456");
        return dto;
    }
}
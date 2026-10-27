package com.campus.express.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.express.common.BusinessException;
import com.campus.express.common.OrderStatus;
import com.campus.express.common.TransferStatus;
import com.campus.express.dto.OrderPublishDTO;
import com.campus.express.dto.OrderUpdateDTO;
import com.campus.express.entity.Order;
import com.campus.express.entity.OrderStatusHistory;
import com.campus.express.entity.OrderTransfer;
import com.campus.express.vo.OrderDetailVO;
import com.campus.express.vo.OrderVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderServiceImpl 稀疏分支补齐单元测试
 *
 * <p>针对 JaCoCo 报告中标红的分支做定向补齐：发布/修改的必填项为空、各方法「订单不存在」早退、
 * 催单在取件中/配送中的状态与文案分支、接单者取消在取件中状态的路径、转单原因为空的校验、
 * 照片上传 file=null 与无扩展名分支、大厅/接力关键词为 null 的归一化分支、非参与者预览接力中订单
 * 的脱敏分支，以及超时未送达时接力失效提示的分支。</p>
 */
class OrderServiceImplBranchFillTest extends AbstractOrderServiceTest {

    // ---------------- 发布：必填项为空 ----------------

    @Test
    @DisplayName("发布：报酬金额为 null → 拒绝")
    void publishRejectsNullReward() {
        OrderPublishDTO dto = publishDto();
        dto.setRewardAmount(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.publishOrder(dto));
        assertTrue(ex.getMessage().contains("报酬金额不能为空"));
    }

    @Test
    @DisplayName("发布：期望取件时间为 null → 拒绝")
    void publishRejectsNullExpectTime() {
        OrderPublishDTO dto = publishDto();
        dto.setExpectTime(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.publishOrder(dto));
        assertTrue(ex.getMessage().contains("期望取件时间不能为空"));
    }

    @Test
    @DisplayName("发布：取件码为 null → 拒绝")
    void publishRejectsNullPickupCode() {
        OrderPublishDTO dto = publishDto();
        dto.setPickupCode(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.publishOrder(dto));
        assertTrue(ex.getMessage().contains("取件码不能为空"));
    }

    // ---------------- 修改待接单：订单不存在 ----------------

    @Test
    @DisplayName("修改待接单：订单不存在 → 拒绝")
    void updateWaitRejectsMissingOrder() {
        when(orderMapper.selectById(10)).thenReturn(null);
        OrderUpdateDTO dto = new OrderUpdateDTO();
        dto.setOrderId(10);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateWaitOrder(dto));
        assertTrue(ex.getMessage().contains("订单不存在"));
    }

    // ---------------- 催单：取件中 / 配送中状态与文案 ----------------

    @Test
    @DisplayName("催单：取件中状态允许，文案为「尽快送达」")
    void urgeAllowedWhenPicking() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.PICKING, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));

        service.urgeTaker(10);

        verify(notificationService).send(eq(2), eq(10), contains("尽快送达"));
    }

    @Test
    @DisplayName("催单：配送中状态允许")
    void urgeAllowedWhenDelivering() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.DELIVERING, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));

        service.urgeTaker(10);

        verify(notificationService).send(eq(2), eq(10), contains("尽快送达"));
    }

    // ---------------- 取消：取件中状态由接单者取消 ----------------

    @Test
    @DisplayName("取消：取件中状态由接单者取消，退回待接单并通知发布者")
    void cancelAcceptedWhenPicking() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.PICKING, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.update(any(), any())).thenReturn(1);

        service.cancelOrder(10, "临时有事/时间冲突");

        verify(notificationService).send(eq(1), eq(10), contains("重新进入待接单"));
    }

    // ---------------- 转单：原因为空 / 订单不存在 ----------------

    @Test
    @DisplayName("发起转单：原因为 null → 拒绝")
    void initiateTransferRejectsNullReason() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(transferMapper.selectCount(any())).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.initiateTransfer(10, null));
        assertTrue(ex.getMessage().contains("请选择转单原因"));
    }

    @Test
    @DisplayName("发起转单：订单不存在 → 拒绝")
    void initiateTransferMissingOrder() {
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.initiateTransfer(10, "临时有事/时间冲突"));
        assertTrue(ex.getMessage().contains("订单不存在"));
    }

    @Test
    @DisplayName("撤回转单：订单不存在 → 拒绝")
    void cancelTransferMissingOrder() {
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectById(10)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancelTransfer(10));
        assertTrue(ex.getMessage().contains("订单不存在"));
    }

    // ---------------- 照片上传：file 为空 / 无扩展名 ----------------

    @Test
    @DisplayName("上传照片：file 为 null → 拒绝")
    void uploadPhotoRejectsNullFile() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.uploadOrderPhoto(10, "pickup", null));
        assertTrue(ex.getMessage().contains("请选择要上传的图片"));
    }

    @Test
    @DisplayName("上传照片：文件名无扩展名 → 拒绝")
    void uploadPhotoRejectsNoExtension() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.uploadOrderPhoto(10, "pickup", file("avatar", jpegHeader(), 1024)));
        assertTrue(ex.getMessage().contains("仅支持 jpg/png/gif/webp"));
    }

    // ---------------- 分页：关键词为 null 的归一化分支 ----------------

    @Test
    @DisplayName("需求大厅：关键词为 null 时透传 null")
    void hallPageNullKeyword() {
        when(orderMapper.selectHallPage(any(), any(), any(), any())).thenReturn(new Page<OrderVO>());

        service.getHallOrderPage(1, 10, null, null, null);

        verify(orderMapper).selectHallPage(any(), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("接力专区：关键词为 null 时透传 null")
    void relayPageNullKeyword() {
        when(orderMapper.selectRelayPage(any(), any(), any(), any())).thenReturn(new Page<OrderVO>());

        service.getRelayOrderPage(1, 10, null, null, null);

        verify(orderMapper).selectRelayPage(any(), isNull(), isNull(), isNull());
    }

    // ---------------- 详情：非参与者预览接力中订单（脱敏） ----------------

    @Test
    @DisplayName("详情：非参与者可预览接力中订单，且取件码与照片被脱敏")
    void detailMasksOutsiderForPendingTransfer() {
        OrderVO vo = new OrderVO();
        vo.setOrderId(10);
        vo.setStatus(OrderStatus.ACCEPTED);
        vo.setPublisherId(1);
        vo.setTakerId(2);
        vo.setTransferStatus(TransferStatus.PENDING);
        vo.setPickupCode("1234");
        vo.setPickupPhoto("/uploads/order/p.jpg");
        vo.setDeliverPhoto("/uploads/order/d.jpg");
        when(orderMapper.selectOrderVOById(10)).thenReturn(vo);
        when(userService.getCurrentUser()).thenReturn(user(9, "路人", 100, false));

        OrderDetailVO detail = service.getOrderVOById(10);

        assertNull(vo.getPickupCode());
        assertNull(vo.getPickupPhoto());
        assertNull(vo.getDeliverPhoto());
        assertTrue(detail.getOrder() == vo);
    }

    // ---------------- 超时未送达：接力失效提示 ----------------

    @Test
    @DisplayName("超时未送达：订单处于接力中时，通知接单者接力已失效")
    void overdueDeliveryNotifiesRelayInvalidation() {
        Order overdue = order(12, OrderStatus.PICKING, 1, 3);
        overdue.setTransferStatus(TransferStatus.PENDING);
        // selectList 调用顺序：⓪过期未接单 / ①超时提醒 / ②未取件 / ③未送达
        when(orderMapper.selectList(any())).thenReturn(List.of(), List.of(), List.of(), List.of(overdue));
        when(orderMapper.update(any(), any())).thenReturn(1);

        service.timeoutRemindCancelTask();

        verify(notificationService).send(eq(3), eq(12), contains("转单接力已随之失效"));
    }

    // ---------------- 确认送达：已上传送达凭证的文案分支 ----------------

    @Test
    @DisplayName("确认送达：已上传送达凭证时，历史记录文案为「已上传送达凭证」")
    void confirmArrivalWithPhoto() {
        Order o = order(10, OrderStatus.DELIVERING, 1, 2);
        o.setDeliverPhoto("/uploads/order/d.jpg");
        when(orderMapper.selectById(10)).thenReturn(o);
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        service.confirmArrival(10, false);

        ArgumentCaptor<OrderStatusHistory> captor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyService).saveHistoryRecord(captor.capture());
        assertTrue(captor.getValue().getChangeNote().contains("已上传送达凭证"));
    }

    // ---------------- 照片上传：原始文件名为 null ----------------

    @Test
    @DisplayName("上传照片：原始文件名为 null → 拒绝（无扩展名）")
    void uploadPhotoRejectsNullFilename() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        MultipartFile f = mock(MultipartFile.class);
        when(f.isEmpty()).thenReturn(false);
        when(f.getOriginalFilename()).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.uploadOrderPhoto(10, "pickup", f));
        assertTrue(ex.getMessage().contains("仅支持 jpg/png/gif/webp"));
    }

    // ---------------- 转单：取件中状态成功 / 原因为空白 ----------------

    @Test
    @DisplayName("发起转单：取件中状态允许发起，写入接力流水")
    void initiateTransferAllowedWhenPicking() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.PICKING, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(transferMapper.selectCount(any())).thenReturn(0L);
        when(orderMapper.update(any(), any())).thenReturn(1);

        service.initiateTransfer(10, "临时有事/时间冲突");

        verify(transferMapper).insert(any(OrderTransfer.class));
        verify(webSocketSessionManager).broadcast("new_relay");
    }

    @Test
    @DisplayName("发起转单：原因为空白字符串 → 拒绝")
    void initiateTransferRejectsBlankReason() {
        when(orderMapper.selectById(10)).thenReturn(order(10, OrderStatus.ACCEPTED, 1, 2));
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(transferMapper.selectCount(any())).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.initiateTransfer(10, "   "));
        assertTrue(ex.getMessage().contains("请选择转单原因"));
    }

    // ---------------- 接力：取件中且期望取件时间为空 ----------------

    @Test
    @DisplayName("接力接单：取件中状态且期望取件时间为空时允许接力")
    void relayAllowedWhenPickingWithNullExpectTime() {
        Order o = order(10, OrderStatus.PICKING, 1, 3);
        o.setTransferStatus(TransferStatus.PENDING);
        o.setExpectTime(null);
        when(orderMapper.selectById(10)).thenReturn(o);
        when(userService.getCurrentUser()).thenReturn(user(2, "接力者", 100, false));
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(orderMapper.relayTakeOrder(any(), any(), any(), any())).thenReturn(1);
        when(userService.getById(3)).thenReturn(user(3, "原接单者", 100, false));

        service.relayTakeOrder(10);

        verify(transferMapper).update(any(), any());
        verify(notificationService).send(eq(3), eq(10), contains("接力承接"));
    }

    // ---------------- 取消：期望取件时间为空时的退回通知（无追加提示） ----------------

    @Test
    @DisplayName("取消：接单者取消且期望取件时间为空时，通知不含「取件时间已过」追加提示")
    void cancelAcceptedWithNullExpectTime() {
        Order o = order(10, OrderStatus.PICKING, 1, 2);
        o.setExpectTime(null);
        when(orderMapper.selectById(10)).thenReturn(o);
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.update(any(), any())).thenReturn(1);

        service.cancelOrder(10, "临时有事/时间冲突");

        verify(notificationService).send(eq(1), eq(10), contains("重新进入待接单"));
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

    private MultipartFile file(String name, byte[] content, long size) {
        MultipartFile f = mock(MultipartFile.class);
        when(f.isEmpty()).thenReturn(false);
        when(f.getOriginalFilename()).thenReturn(name);
        try {
            when(f.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        when(f.getSize()).thenReturn(size);
        return f;
    }

    private byte[] jpegHeader() {
        return new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
        };
    }
}
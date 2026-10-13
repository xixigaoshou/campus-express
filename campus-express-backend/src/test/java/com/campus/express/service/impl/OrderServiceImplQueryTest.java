package com.campus.express.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.express.common.OrderStatus;
import com.campus.express.entity.Evaluation;
import com.campus.express.vo.OrderVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单分页查询与「是否已评价」标记填充单元测试
 *
 * <p>覆盖：需求大厅/接力专区关键词归一化（空白转 null、非空去空格）、我的发布/我的接单
 * 的发布者/接单者维度区分，以及 {@code fillEvaluatedFlag} 对已完成订单批量回填 myEvaluated
 * 的三条分支（无记录、无已完成、命中评价集合）。</p>
 */
class OrderServiceImplQueryTest extends AbstractOrderServiceTest {

    // ---------------- 需求大厅 / 接力专区：关键词归一化 ----------------

    @Test
    @DisplayName("需求大厅：空白关键词归一化为 null，报酬范围透传")
    void hallPageNormalizesBlankKeyword() {
        when(orderMapper.selectHallPage(any(), any(), any(), any())).thenReturn(new Page<OrderVO>());

        service.getHallOrderPage(1, 10, "   ", new BigDecimal("2.00"), new BigDecimal("50.00"));

        verify(orderMapper).selectHallPage(any(), isNull(),
                eq(new BigDecimal("2.00")), eq(new BigDecimal("50.00")));
    }

    @Test
    @DisplayName("需求大厅：关键词去除首尾空格后透传")
    void hallPageTrimsKeyword() {
        when(orderMapper.selectHallPage(any(), any(), any(), any())).thenReturn(new Page<OrderVO>());

        service.getHallOrderPage(1, 10, "  顺丰  ", null, null);

        verify(orderMapper).selectHallPage(any(), eq("顺丰"), isNull(), isNull());
    }

    @Test
    @DisplayName("接力专区：空白关键词归一化为 null")
    void relayPageNormalizesBlankKeyword() {
        when(orderMapper.selectRelayPage(any(), any(), any(), any())).thenReturn(new Page<OrderVO>());

        service.getRelayOrderPage(1, 10, "  ", null, null);

        verify(orderMapper).selectRelayPage(any(), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("接力专区：关键词去空格后透传")
    void relayPageTrimsKeyword() {
        when(orderMapper.selectRelayPage(any(), any(), any(), any())).thenReturn(new Page<OrderVO>());

        service.getRelayOrderPage(1, 10, " 菜鸟 ", null, null);

        verify(orderMapper).selectRelayPage(any(), eq("菜鸟"), isNull(), isNull());
    }

    // ---------------- 我的发布 / 我的接单：维度区分 ----------------

    @Test
    @DisplayName("我的发布：以当前用户为发布者维度查询")
    void myPublishUsesPublisherId() {
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));
        when(orderMapper.selectMyPage(any(), eq(1), isNull(), any())).thenReturn(emptyPage());

        service.getMyPublishPage(1, 10, null);

        verify(orderMapper).selectMyPage(any(), eq(1), isNull(), any());
    }

    @Test
    @DisplayName("我的接单：以当前用户为接单者维度查询，无记录时不查评价")
    void myTakeUsesTakerId() {
        when(userService.getCurrentUser()).thenReturn(user(2, "接单者", 100, false));
        when(orderMapper.selectMyPage(any(), isNull(), eq(2), any())).thenReturn(emptyPage());

        service.getMyTakePage(1, 10, List.of(OrderStatus.ACTIVE));

        verify(orderMapper).selectMyPage(any(), isNull(), eq(2), any());
        verify(evaluationMapper, never()).selectList(any());
    }

    // ---------------- fillEvaluatedFlag ----------------

    @Test
    @DisplayName("已评价标记：无已完成订单时跳过评价查询")
    void fillEvaluatedSkipsWhenNoCompletedOrder() {
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));
        Page<OrderVO> page = pageOf(vo(101, OrderStatus.ACCEPTED));
        when(orderMapper.selectMyPage(any(), eq(1), isNull(), any())).thenReturn(page);

        service.getMyPublishPage(1, 10, null);

        verify(evaluationMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("已评价标记：已完成订单命中评价集合为 true，未命中为 false，非完成订单保持 null")
    void fillEvaluatedMarksCompletedOrders() {
        when(userService.getCurrentUser()).thenReturn(user(1, "发布者", 100, true));
        OrderVO evaluated = vo(100, OrderStatus.COMPLETED);
        OrderVO notEvaluated = vo(101, OrderStatus.COMPLETED);
        OrderVO active = vo(102, OrderStatus.ACCEPTED);
        Page<OrderVO> page = pageOf(evaluated, notEvaluated, active);
        when(orderMapper.selectMyPage(any(), eq(1), isNull(), any())).thenReturn(page);
        Evaluation e = new Evaluation();
        e.setOrderId(100);
        when(evaluationMapper.selectList(any())).thenReturn(List.of(e));

        service.getMyPublishPage(1, 10, null);

        assertTrue(evaluated.getMyEvaluated());
        assertFalse(notEvaluated.getMyEvaluated());
        assertNull(active.getMyEvaluated());
    }

    // ---------------- 构造辅助 ----------------

    private Page<OrderVO> emptyPage() {
        return new Page<>();
    }

    private Page<OrderVO> pageOf(OrderVO... records) {
        Page<OrderVO> page = new Page<>(1, 10);
        page.setRecords(new ArrayList<>(List.of(records)));
        return page;
    }

    private OrderVO vo(int id, String status) {
        OrderVO v = new OrderVO();
        v.setOrderId(id);
        v.setStatus(status);
        v.setPublisherId(1);
        v.setRewardAmount(new BigDecimal("5.00"));
        return v;
    }
}
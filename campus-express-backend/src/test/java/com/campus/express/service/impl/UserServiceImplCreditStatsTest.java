package com.campus.express.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.express.common.BusinessException;
import com.campus.express.common.OrderStatus;
import com.campus.express.entity.CreditLog;
import com.campus.express.entity.User;
import com.campus.express.vo.UserStatsVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户「信誉分 / 个人统计 / 信誉等级」单元测试
 *
 * <p>重点覆盖：信誉分封顶后不再写流水（避免 +0 噪音记录）、更新失败的用户不存在分支、
 * 流水增量为实际差值、个人统计全量聚合口径、信誉等级按区间划分。</p>
 */
class UserServiceImplCreditStatsTest extends AbstractUserServiceTest {

    // ---------------- 信誉分增减 ----------------

    @Test
    @DisplayName("信誉分增减：用户不存在 → 拒绝")
    void updateCreditScoreRejectsUnknownUser() {
        when(userMapper.selectById(1)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateCreditScore(1, 1, "评价好评", "订单#1", 1));
        assertTrue(ex.getMessage().contains("用户不存在"));
    }

    @Test
    @DisplayName("信誉分增减：SQL 更新影响 0 行 → 拒绝且不写流水")
    void updateCreditScoreFailsWhenNoRowAffected() {
        when(userMapper.selectById(1)).thenReturn(user(1, "张三", 100, 1));
        when(userMapper.updateCreditScore(1, 1, rules.getCredit().getMaxScore())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateCreditScore(1, 1, "评价好评", "订单#1", 1));
        assertTrue(ex.getMessage().contains("信誉分更新失败"));
        verify(creditLogMapper, never()).insert(any(CreditLog.class));
    }

    @Test
    @DisplayName("信誉分增加：按实际增量写流水（含关联订单号）")
    void updateCreditScoreWritesLogWithActualDelta() {
        when(userMapper.selectById(1))
                .thenReturn(user(1, "张三", 100, 1))
                .thenReturn(user(1, "张三", 101, 1));
        when(userMapper.updateCreditScore(1, 1, rules.getCredit().getMaxScore())).thenReturn(1);

        service.updateCreditScore(1, 1, "评价好评", "订单#9", 9);

        ArgumentCaptor<CreditLog> captor = ArgumentCaptor.forClass(CreditLog.class);
        verify(creditLogMapper).insert(captor.capture());
        CreditLog log = captor.getValue();
        assertEquals(1, log.getChangeAmount());
        assertEquals(100, log.getBeforeScore());
        assertEquals(101, log.getAfterScore());
        assertEquals(9, log.getRelatedOrderId());
    }

    @Test
    @DisplayName("信誉分扣减：负增量如实写入流水")
    void updateCreditScoreWritesNegativeDelta() {
        when(userMapper.selectById(1))
                .thenReturn(user(1, "张三", 100, 1))
                .thenReturn(user(1, "张三", 95, 1));
        when(userMapper.updateCreditScore(1, -5, rules.getCredit().getMaxScore())).thenReturn(1);

        service.updateCreditScore(1, -5, "接单者取消", "订单#9", 9);

        ArgumentCaptor<CreditLog> captor = ArgumentCaptor.forClass(CreditLog.class);
        verify(creditLogMapper).insert(captor.capture());
        assertEquals(-5, captor.getValue().getChangeAmount());
    }

    @Test
    @DisplayName("信誉分已达上限：封顶后实际增量为 0，不写流水（避免 +0 噪音）")
    void updateCreditScoreSkipsLogWhenCapped() {
        when(userMapper.selectById(1))
                .thenReturn(user(1, "张三", 150, 1))
                .thenReturn(user(1, "张三", 150, 1));
        when(userMapper.updateCreditScore(1, 1, rules.getCredit().getMaxScore())).thenReturn(1);

        service.updateCreditScore(1, 1, "评价好评", "订单#9", 9);

        verify(creditLogMapper, never()).insert(any(CreditLog.class));
    }

    // ---------------- 信誉分流水 ----------------

    @Test
    @DisplayName("信誉分流水：仅查询当前登录用户自己的记录")
    void pageCreditLogsScopedToCurrentUser() {
        loggedInAs(1);
        when(userMapper.selectById(1)).thenReturn(user(1, "张三", 100, 1));
        when(creditLogMapper.selectPage(any(), any())).thenReturn(new Page<CreditLog>());

        IPage<CreditLog> page = service.pageCreditLogs(1, 10);

        assertEquals(1, page.getCurrent());
        verify(creditLogMapper).selectPage(any(), any());
    }

    // ---------------- 个人中心统计 ----------------

    @Test
    @DisplayName("个人统计：后端全量聚合，各项口径正确")
    void getUserStatsAggregatesAllFields() {
        loggedInAs(1);
        when(userMapper.selectById(1)).thenReturn(user(1, "张三", 100, 1));
        // 发布总数 / 发布取消数 / 接单完成数
        when(orderMapper.selectCount(any())).thenReturn(5L, 2L, 3L);
        when(orderMapper.sumRewardByTaker(1, OrderStatus.COMPLETED)).thenReturn(new BigDecimal("88.00"));
        when(orderMapper.sumRewardByPublisher(1, OrderStatus.COMPLETED)).thenReturn(new BigDecimal("66.00"));
        when(creditLogMapper.selectCount(any())).thenReturn(1L);
        when(orderMapper.countPendingEvaluation(1)).thenReturn(4L);

        UserStatsVO stats = service.getUserStats();

        assertEquals(5L, stats.getPublishTotal());
        assertEquals(2L, stats.getPublishCancelled());
        assertEquals(3L, stats.getTakeCompleted());
        assertEquals(1L, stats.getTakeCancelled());
        assertEquals(new BigDecimal("88.00"), stats.getIncome());
        assertEquals(new BigDecimal("66.00"), stats.getExpense());
        assertEquals(4L, stats.getPendingEvaluation());
    }

    // ---------------- 信誉等级 / 按 ID 查询 ----------------

    @Test
    @DisplayName("信誉等级：按分数区间划分普通/良好/优秀")
    void creditLevelByScoreRange() {
        assertEquals("普通", service.creditLevel(user(1, "张三", 80, 1)));
        assertEquals("良好", service.creditLevel(user(1, "张三", 100, 1)));
        assertEquals("优秀", service.creditLevel(user(1, "张三", 120, 1)));
    }

    @Test
    @DisplayName("按 ID 查询：用户不存在 → 拒绝")
    void getByIdRejectsUnknownUser() {
        when(userMapper.selectById(99)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(99));
        assertTrue(ex.getMessage().contains("用户不存在"));
    }

    @Test
    @DisplayName("按 ID 查询：正常返回用户")
    void getByIdReturnsUser() {
        when(userMapper.selectById(1)).thenReturn(user(1, "张三", 100, 1));

        assertEquals(1, service.getById(1).getUserId());
    }
}
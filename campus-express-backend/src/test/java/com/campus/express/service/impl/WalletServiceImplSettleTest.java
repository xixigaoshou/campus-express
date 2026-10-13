package com.campus.express.service.impl;

import com.campus.express.common.BusinessException;
import com.campus.express.common.WalletLogType;
import com.campus.express.entity.WalletLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钱包「订单结算」单元测试（R-28）
 *
 * <p>重点覆盖：结算幂等（发布者确认收货与定时任务超时自动确认并发时不重复扣款/入账）、
 * 结算前按订单净冻结额补冻结差额（避免冻结不足失败或多冻结差额永久滞留）。</p>
 */
class WalletServiceImplSettleTest extends AbstractWalletServiceTest {

    @Test
    @DisplayName("结算：该订单已存在报酬支出流水 → 幂等直接返回，不扣款不入账")
    void settleIsIdempotentWhenAlreadySettled() {
        when(walletLogMapper.selectCount(any())).thenReturn(1L);

        service.settleOrderReward(1, 2, 10, new BigDecimal("5.00"));

        verify(walletMapper, never()).freeze(any(), any());
        verify(walletMapper, never()).settlePayFrozen(any(), any());
        verify(walletMapper, never()).settleIncome(any(), any());
        verify(walletLogMapper, never()).insert(any(WalletLog.class));
    }

    @Test
    @DisplayName("结算：订单净冻结额不足以覆盖报酬 → 先补冻结差额再结算")
    void settleTopsUpFrozenShortfall() {
        when(walletLogMapper.selectCount(any())).thenReturn(0L);
        when(walletLogMapper.selectList(any()))
                .thenReturn(List.of(log(WalletLogType.ORDER_FROZEN.getCode(), "-3.00")));
        when(walletMapper.selectOne(any())).thenReturn(wallet(1, "100.00", "3.00"));
        when(walletMapper.freeze(any(), any())).thenReturn(1);
        when(walletMapper.settlePayFrozen(any(), any())).thenReturn(1);
        when(walletMapper.settleIncome(any(), any())).thenReturn(1);

        service.settleOrderReward(1, 2, 10, new BigDecimal("5.00"));

        // 差额 = 5 - 3 = 2，需补冻结
        verify(walletMapper).freeze(1, new BigDecimal("2.00"));
        verify(walletMapper).settlePayFrozen(1, new BigDecimal("5.00"));
        verify(walletMapper).settleIncome(2, new BigDecimal("5.00"));
        // 补冻结流水 1 条 + 支出流水 1 条 + 收入流水 1 条
        verify(walletLogMapper, times(3)).insert(any(WalletLog.class));
    }

    @Test
    @DisplayName("结算：订单净冻结额已覆盖报酬 → 不补冻结，直接结算")
    void settleSkipsTopUpWhenFullyFrozen() {
        when(walletLogMapper.selectCount(any())).thenReturn(0L);
        when(walletLogMapper.selectList(any()))
                .thenReturn(List.of(log(WalletLogType.ORDER_FROZEN.getCode(), "-5.00")));
        when(walletMapper.selectOne(any())).thenReturn(wallet(1, "100.00", "5.00"));
        when(walletMapper.settlePayFrozen(any(), any())).thenReturn(1);
        when(walletMapper.settleIncome(any(), any())).thenReturn(1);

        service.settleOrderReward(1, 2, 10, new BigDecimal("5.00"));

        verify(walletMapper, never()).freeze(any(), any());
        verify(walletLogMapper, times(2)).insert(any(WalletLog.class));
    }

    @Test
    @DisplayName("结算：发布者冻结余额不足 → 抛业务异常，不写任何流水")
    void settleThrowsWhenPayFrozenInsufficient() {
        when(walletLogMapper.selectCount(any())).thenReturn(0L);
        when(walletLogMapper.selectList(any()))
                .thenReturn(List.of(log(WalletLogType.ORDER_FROZEN.getCode(), "-5.00")));
        when(walletMapper.selectOne(any())).thenReturn(wallet(1, "100.00", "5.00"));
        when(walletMapper.settlePayFrozen(any(), any())).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> service.settleOrderReward(1, 2, 10, new BigDecimal("5.00")));

        verify(walletMapper, never()).settleIncome(any(), any());
        verify(walletLogMapper, never()).insert(any(WalletLog.class));
    }

    @Test
    @DisplayName("结算成功：发布者支出为负、接单者收入为正，前后余额正确")
    void settleWritesBothLogs() {
        when(walletLogMapper.selectCount(any())).thenReturn(0L);
        when(walletLogMapper.selectList(any()))
                .thenReturn(List.of(log(WalletLogType.ORDER_FROZEN.getCode(), "-5.00")));
        when(walletMapper.selectOne(any())).thenReturn(wallet(1, "100.00", "5.00"));
        when(walletMapper.settlePayFrozen(any(), any())).thenReturn(1);
        when(walletMapper.settleIncome(any(), any())).thenReturn(1);

        service.settleOrderReward(1, 2, 10, new BigDecimal("5.00"));

        ArgumentCaptor<WalletLog> captor = ArgumentCaptor.forClass(WalletLog.class);
        verify(walletLogMapper, times(2)).insert(captor.capture());
        List<WalletLog> logs = captor.getAllValues();

        WalletLog payLog = findByType(logs, WalletLogType.ORDER_PAY.getCode());
        assertEquals(1, payLog.getUserId());
        assertEquals(new BigDecimal("-5.00"), payLog.getAmount());
        assertEquals(new BigDecimal("5.00"), payLog.getBeforeFrozen());
        assertEquals(new BigDecimal("0.00"), payLog.getAfterFrozen());

        WalletLog incomeLog = findByType(logs, WalletLogType.ORDER_INCOME.getCode());
        assertEquals(2, incomeLog.getUserId());
        assertEquals(new BigDecimal("5.00"), incomeLog.getAmount());
        assertEquals(new BigDecimal("100.00"), incomeLog.getBeforeBalance());
        assertEquals(new BigDecimal("105.00"), incomeLog.getAfterBalance());
    }

    private WalletLog findByType(List<WalletLog> logs, int logType) {
        return logs.stream().filter(l -> l.getLogType() == logType).findFirst()
                .orElseThrow(() -> new AssertionError("未找到流水类型 " + logType));
    }
}
package com.campus.express.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.express.common.BusinessException;
import com.campus.express.common.WalletLogType;
import com.campus.express.entity.WalletLog;
import com.campus.express.vo.WalletLogVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钱包「充值 / 提现 / 流水分页」单元测试
 *
 * <p>重点覆盖：提现的余额校验与原子条件更新兜底、充值/提现流水字段正确性、
 * 流水分页的类型文本映射（含未知类型兜底）。</p>
 */
class WalletServiceImplRechargeWithdrawTest extends AbstractWalletServiceTest {

    // ---------------- 充值 ----------------

    @Test
    @DisplayName("充值：增加可用余额并写充值流水（金额为正）")
    void rechargeIncreasesBalanceAndWritesLog() {
        when(walletMapper.selectOne(any())).thenReturn(wallet(1, "10.00", "0.00"));
        when(walletMapper.recharge(any(), any())).thenReturn(1);

        service.recharge(1, new BigDecimal("20.00"));

        verify(walletMapper).recharge(1, new BigDecimal("20.00"));
        WalletLog log = captureSingleLog();
        assertEquals(WalletLogType.RECHARGE.getCode(), log.getLogType());
        assertEquals(new BigDecimal("20.00"), log.getAmount());
        assertEquals(new BigDecimal("10.00"), log.getBeforeBalance());
        assertEquals(new BigDecimal("30.00"), log.getAfterBalance());
    }

    // ---------------- 提现 ----------------

    @Test
    @DisplayName("提现：可用余额不足 → 抛业务异常，不扣款、不写流水")
    void withdrawRejectsWhenBalanceInsufficient() {
        when(walletMapper.selectOne(any())).thenReturn(wallet(1, "3.00", "0.00"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.withdraw(1, new BigDecimal("5.00")));

        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("余额不足"));
        verify(walletMapper, never()).withdraw(any(), any());
        verify(walletLogMapper, never()).insert(any(WalletLog.class));
    }

    @Test
    @DisplayName("提现：条件更新返回 0（并发被抢先扣减）→ 抛业务异常，不写流水")
    void withdrawRejectsWhenAtomicUpdateZero() {
        when(walletMapper.selectOne(any())).thenReturn(wallet(1, "100.00", "0.00"));
        when(walletMapper.withdraw(any(), any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.withdraw(1, new BigDecimal("20.00")));

        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("提现失败"));
        verify(walletLogMapper, never()).insert(any(WalletLog.class));
    }

    @Test
    @DisplayName("提现成功：扣减可用余额并写提现流水（金额为负）")
    void withdrawSuccessWritesLog() {
        when(walletMapper.selectOne(any())).thenReturn(wallet(1, "100.00", "0.00"));
        when(walletMapper.withdraw(any(), any())).thenReturn(1);

        service.withdraw(1, new BigDecimal("20.00"));

        verify(walletMapper).withdraw(1, new BigDecimal("20.00"));
        WalletLog log = captureSingleLog();
        assertEquals(WalletLogType.WITHDRAW.getCode(), log.getLogType());
        assertEquals(new BigDecimal("-20.00"), log.getAmount());
        assertEquals(new BigDecimal("100.00"), log.getBeforeBalance());
        assertEquals(new BigDecimal("80.00"), log.getAfterBalance());
    }

    // ---------------- 流水分页 ----------------

    @Test
    @DisplayName("流水分页：类型 code 转文本，未知 code 兜底为「未知类型」")
    void pageWalletLogsConvertsTypeText() {
        WalletLog income = log(WalletLogType.ORDER_INCOME.getCode(), "5.00");
        income.setCreateTime(LocalDateTime.now());
        WalletLog unknown = log(999, "-3.00");
        Page<WalletLog> page = new Page<>(1, 10);
        page.setRecords(List.of(income, unknown));
        page.setTotal(2);
        doReturn(page).when(walletLogMapper).selectPage(any(), any());

        IPage<WalletLogVO> result = service.pageWalletLogs(1, 1, 10, null);

        assertEquals(2, result.getRecords().size());
        assertEquals("订单结算收入", result.getRecords().get(0).getLogTypeText());
        assertEquals("未知类型", result.getRecords().get(1).getLogTypeText());
        verify(walletLogMapper).selectPage(any(), any());
    }

    @Test
    @DisplayName("流水分页：传入类型过滤时仍正常返回分页结果")
    void pageWalletLogsWithTypeFilter() {
        Page<WalletLog> page = new Page<>(1, 10);
        page.setRecords(List.of(log(WalletLogType.RECHARGE.getCode(), "20.00")));
        page.setTotal(1);
        doReturn(page).when(walletLogMapper).selectPage(any(), any());

        IPage<WalletLogVO> result = service.pageWalletLogs(1, 1, 10, WalletLogType.RECHARGE.getCode());

        assertEquals(1, result.getRecords().size());
        assertEquals("充值", result.getRecords().get(0).getLogTypeText());
    }

    private WalletLog captureSingleLog() {
        ArgumentCaptor<WalletLog> captor = ArgumentCaptor.forClass(WalletLog.class);
        verify(walletLogMapper).insert(captor.capture());
        return captor.getValue();
    }
}
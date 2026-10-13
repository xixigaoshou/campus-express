package com.campus.express.service.impl;

import com.campus.express.common.BusinessException;
import com.campus.express.common.WalletLogType;
import com.campus.express.entity.Wallet;
import com.campus.express.entity.WalletLog;
import com.campus.express.vo.WalletVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钱包「查询 / 冻结 / 解冻 / 差额调整」单元测试（R-25 ~ R-27）
 *
 * <p>重点覆盖：可用余额校验、原子条件更新返回 0 的并发兜底、订单维度净冻结幂等
 * （避免把其他订单的冻结额错误退还）、解冻金额以净冻结额为上限防资金侵占。</p>
 */
class WalletServiceImplFreezeTest extends AbstractWalletServiceTest {

    // ---------------- 查询钱包 ----------------

    @Test
    @DisplayName("查询钱包：已存在 → 直接返回余额，不创建")
    void getWalletInfoReturnsExisting() {
        when(walletMapper.selectOne(any())).thenReturn(wallet(1, "50.00", "10.00"));

        WalletVO vo = service.getWalletInfo(1);

        assertEquals(1, vo.getUserId());
        assertEquals(new BigDecimal("50.00"), vo.getBalance());
        assertEquals(new BigDecimal("10.00"), vo.getFrozenBalance());
        verify(walletMapper, never()).insert(any(Wallet.class));
    }

    @Test
    @DisplayName("查询钱包：不存在 → 自动创建零余额钱包（兼容存量账号）")
    void getWalletInfoCreatesWhenAbsent() {
        when(walletMapper.selectOne(any())).thenReturn(null);

        WalletVO vo = service.getWalletInfo(7);

        verify(walletMapper).insert(any(Wallet.class));
        assertEquals(7, vo.getUserId());
        assertEquals(BigDecimal.ZERO, vo.getBalance());
        assertEquals(BigDecimal.ZERO, vo.getFrozenBalance());
    }

    // ---------------- 冻结报酬 R-25/R-26 ----------------

    @Test
    @DisplayName("冻结：可用余额不足 → 抛业务异常，不更新余额、不写流水")
    void freezeRejectsWhenBalanceInsufficient() {
        when(walletMapper.selectOne(any())).thenReturn(wallet(1, "3.00", "0.00"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.freezeBalance(1, 10, new BigDecimal("5.00")));

        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("余额不足"));
        verify(walletMapper, never()).freeze(any(), any());
        verify(walletLogMapper, never()).insert(any(WalletLog.class));
    }

    @Test
    @DisplayName("冻结：余额校验通过但条件更新返回 0（并发被抢先扣减）→ 抛业务异常")
    void freezeRejectsWhenAtomicUpdateZero() {
        when(walletMapper.selectOne(any())).thenReturn(wallet(1, "100.00", "0.00"));
        when(walletMapper.freeze(any(), any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.freezeBalance(1, 10, new BigDecimal("5.00")));

        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("冻结失败"));
        verify(walletLogMapper, never()).insert(any(WalletLog.class));
    }

    @Test
    @DisplayName("冻结：成功 → 原子扣减 + 写冻结流水（金额为负，前后余额正确）")
    void freezeSuccessWritesLog() {
        when(walletMapper.selectOne(any())).thenReturn(wallet(1, "100.00", "20.00"));
        when(walletMapper.freeze(any(), any())).thenReturn(1);

        service.freezeBalance(1, 10, new BigDecimal("5.00"));

        verify(walletMapper).freeze(1, new BigDecimal("5.00"));
        WalletLog log = captureSingleLog();
        assertEquals(WalletLogType.ORDER_FROZEN.getCode(), log.getLogType());
        assertEquals(10, log.getRelatedOrderId());
        assertEquals(new BigDecimal("-5.00"), log.getAmount());
        assertEquals(new BigDecimal("100.00"), log.getBeforeBalance());
        assertEquals(new BigDecimal("95.00"), log.getAfterBalance());
        assertEquals(new BigDecimal("20.00"), log.getBeforeFrozen());
        assertEquals(new BigDecimal("25.00"), log.getAfterFrozen());
    }

    @Test
    @DisplayName("冻结：orderId 为空（发布阶段订单未生成）→ 备注不含订单号")
    void freezeWithNullOrderIdUsesBaseRemark() {
        when(walletMapper.selectOne(any())).thenReturn(wallet(1, "100.00", "0.00"));
        when(walletMapper.freeze(any(), any())).thenReturn(1);

        service.freezeBalance(1, null, new BigDecimal("5.00"));

        WalletLog log = captureSingleLog();
        assertEquals("发布订单冻结报酬", log.getRemark());
        assertEquals(null, log.getRelatedOrderId());
    }

    // ---------------- 解冻退回 R-27 ----------------

    @Test
    @DisplayName("解冻：该订单净冻结额已为 0 → 幂等静默返回，不重复退款")
    void unfreezeIsIdempotentWhenNetFrozenZero() {
        when(walletLogMapper.selectList(any()))
                .thenReturn(List.of(log(WalletLogType.ORDER_UNFROZEN.getCode(), "5.00")));

        service.unfreezeBalance(1, 10, new BigDecimal("5.00"));

        verify(walletMapper, never()).unfreeze(any(), any());
        verify(walletLogMapper, never()).insert(any(WalletLog.class));
    }

    @Test
    @DisplayName("解冻：退款额以订单净冻结额为上限（请求 3 < 净冻结 5 → 退 3）")
    void unfreezeCapsRefundAtRequestedAmount() {
        when(walletLogMapper.selectList(any()))
                .thenReturn(List.of(log(WalletLogType.ORDER_FROZEN.getCode(), "-5.00")));
        when(walletMapper.selectOne(any())).thenReturn(wallet(1, "0.00", "5.00"));
        when(walletMapper.unfreeze(any(), any())).thenReturn(1);

        service.unfreezeBalance(1, 10, new BigDecimal("3.00"));

        verify(walletMapper).unfreeze(1, new BigDecimal("3.00"));
        WalletLog log = captureSingleLog();
        assertEquals(WalletLogType.ORDER_UNFROZEN.getCode(), log.getLogType());
        assertEquals(new BigDecimal("3.00"), log.getAmount());
    }

    @Test
    @DisplayName("解冻：请求额超过净冻结额（请求 8 > 净冻结 5 → 只退 5，防侵占他单冻结）")
    void unfreezeCapsRefundAtNetFrozen() {
        when(walletLogMapper.selectList(any()))
                .thenReturn(List.of(log(WalletLogType.ORDER_FROZEN.getCode(), "-5.00")));
        when(walletMapper.selectOne(any())).thenReturn(wallet(1, "0.00", "5.00"));
        when(walletMapper.unfreeze(any(), any())).thenReturn(1);

        service.unfreezeBalance(1, 10, new BigDecimal("8.00"));

        verify(walletMapper).unfreeze(1, new BigDecimal("5.00"));
        assertEquals(new BigDecimal("5.00"), captureSingleLog().getAmount());
    }

    @Test
    @DisplayName("解冻：条件更新返回 0 但钱包冻结额已为 0 → 视为已完成，静默返回")
    void unfreezeSilentlyReturnsWhenFrozenZero() {
        when(walletLogMapper.selectList(any()))
                .thenReturn(List.of(log(WalletLogType.ORDER_FROZEN.getCode(), "-5.00")));
        when(walletMapper.selectOne(any())).thenReturn(wallet(1, "0.00", "0.00"));
        when(walletMapper.unfreeze(any(), any())).thenReturn(0);

        service.unfreezeBalance(1, 10, new BigDecimal("5.00"));

        verify(walletLogMapper, never()).insert(any(WalletLog.class));
    }

    @Test
    @DisplayName("解冻：条件更新返回 0 且钱包仍有冻结额 → 抛业务异常（数据不一致）")
    void unfreezeThrowsWhenFrozenNotZero() {
        when(walletLogMapper.selectList(any()))
                .thenReturn(List.of(log(WalletLogType.ORDER_FROZEN.getCode(), "-5.00")));
        when(walletMapper.selectOne(any())).thenReturn(wallet(1, "0.00", "10.00"));
        when(walletMapper.unfreeze(any(), any())).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> service.unfreezeBalance(1, 10, new BigDecimal("5.00")));
        verify(walletLogMapper, never()).insert(any(WalletLog.class));
    }

    @Test
    @DisplayName("解冻：orderId 为空 → 跳过净冻结推算，按请求额退款")
    void unfreezeWithoutOrderIdSkipsNetFrozenLookup() {
        when(walletMapper.selectOne(any())).thenReturn(wallet(1, "0.00", "5.00"));
        when(walletMapper.unfreeze(any(), any())).thenReturn(1);

        service.unfreezeBalance(1, null, new BigDecimal("5.00"));

        verify(walletLogMapper, never()).selectList(any());
        verify(walletMapper).unfreeze(1, new BigDecimal("5.00"));
    }

    // ---------------- 差额调整（修改待接单订单报酬） ----------------

    @Test
    @DisplayName("差额调整：delta 为 null → 直接返回，无任何资金操作")
    void adjustFrozenNoopWhenDeltaNull() {
        service.adjustFrozenBalance(1, 10, null);

        verify(walletMapper, never()).freeze(any(), any());
        verify(walletMapper, never()).unfreeze(any(), any());
        verify(walletLogMapper, never()).insert(any(WalletLog.class));
    }

    @Test
    @DisplayName("差额调整：delta 为 0 → 直接返回，无任何资金操作")
    void adjustFrozenNoopWhenDeltaZero() {
        service.adjustFrozenBalance(1, 10, new BigDecimal("0.00"));

        verify(walletMapper, never()).freeze(any(), any());
        verify(walletMapper, never()).unfreeze(any(), any());
        verify(walletLogMapper, never()).insert(any(WalletLog.class));
    }

    @Test
    @DisplayName("差额调整：delta > 0 → 追加冻结差额")
    void adjustFrozenFreezesWhenDeltaPositive() {
        when(walletMapper.selectOne(any())).thenReturn(wallet(1, "100.00", "5.00"));
        when(walletMapper.freeze(any(), any())).thenReturn(1);

        service.adjustFrozenBalance(1, 10, new BigDecimal("2.00"));

        verify(walletMapper).freeze(1, new BigDecimal("2.00"));
        WalletLog log = captureSingleLog();
        assertEquals(WalletLogType.ORDER_FROZEN.getCode(), log.getLogType());
        assertEquals(new BigDecimal("-2.00"), log.getAmount());
    }

    @Test
    @DisplayName("差额调整：delta < 0 → 解冻差额绝对值")
    void adjustFrozenUnfreezesWhenDeltaNegative() {
        when(walletLogMapper.selectList(any()))
                .thenReturn(List.of(log(WalletLogType.ORDER_FROZEN.getCode(), "-5.00")));
        when(walletMapper.selectOne(any())).thenReturn(wallet(1, "0.00", "5.00"));
        when(walletMapper.unfreeze(any(), any())).thenReturn(1);

        service.adjustFrozenBalance(1, 10, new BigDecimal("-2.00"));

        verify(walletMapper).unfreeze(1, new BigDecimal("2.00"));
        WalletLog log = captureSingleLog();
        assertEquals(WalletLogType.ORDER_UNFROZEN.getCode(), log.getLogType());
        assertEquals(new BigDecimal("2.00"), log.getAmount());
    }

    private WalletLog captureSingleLog() {
        ArgumentCaptor<WalletLog> captor = ArgumentCaptor.forClass(WalletLog.class);
        verify(walletLogMapper).insert(captor.capture());
        return captor.getValue();
    }
}
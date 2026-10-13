package com.campus.express.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 原因清单与状态常量单元测试
 * 覆盖转单原因校验、取消原因清单完整性、状态机常量约束（纯逻辑，无需数据库）
 */
class ReasonCatalogTest {

    @Test
    @DisplayName("转单原因校验：清单内文案通过，清单外/空值拒绝")
    void transferReasonValidation() {
        assertTrue(TransferReason.isValid("临时有事/时间冲突"));
        assertTrue(TransferReason.isValid("其他原因"));
        assertFalse(TransferReason.isValid("随便写一个不在清单里的理由"));
        assertFalse(TransferReason.isValid(null));
    }

    @Test
    @DisplayName("取消原因清单：发布者/接单者各自独立且均含「其他原因」兜底项")
    void cancelReasonCatalogIntegrity() {
        assertNotNull(CancelReason.PUBLISHER_REASONS);
        assertNotNull(CancelReason.TAKER_REASONS);
        assertFalse(CancelReason.PUBLISHER_REASONS.isEmpty());
        assertFalse(CancelReason.TAKER_REASONS.isEmpty());
        assertTrue(CancelReason.PUBLISHER_REASONS.contains("其他原因"));
        assertTrue(CancelReason.TAKER_REASONS.contains("其他原因"));
    }

    @Test
    @DisplayName("状态机常量：终态不属于进行中，取消合法状态仅限取件前")
    void statusConstants() {
        // 已完成/已取消为终态，不应出现在「进行中」集合
        for (String active : OrderStatus.ACTIVE) {
            assertFalse(OrderStatus.COMPLETED.equals(active));
            assertFalse(OrderStatus.CANCELLED.equals(active));
        }
        // R-2：仅待接单/已接单/取件中可取消，配送中及之后不可取消
        assertTrue(java.util.Arrays.asList(OrderStatus.CANCELABLE).contains(OrderStatus.WAIT_ACCEPT));
        assertTrue(java.util.Arrays.asList(OrderStatus.CANCELABLE).contains(OrderStatus.PICKING));
        assertFalse(java.util.Arrays.asList(OrderStatus.CANCELABLE).contains(OrderStatus.DELIVERING));
        assertFalse(java.util.Arrays.asList(OrderStatus.CANCELABLE).contains(OrderStatus.WAIT_CONFIRM));
    }
}
package com.campus.express.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全局异常处理器单元测试
 *
 * <p>重点覆盖唯一索引冲突：并发「先查后插」撞库时，数据库抛出的
 * {@link DuplicateKeyException} 必须转成可读的 400，而不是落到兜底分支变成 500。</p>
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("学号唯一索引冲突 → 400 可读提示")
    void duplicateStudentNo() {
        Result<Void> r = handler.handleDuplicateKey(
                new DuplicateKeyException("Duplicate entry '2023001' for key 't_user.uk_student_no'"));
        assertEquals(400, r.getCode());
        assertEquals("该学号已注册，请直接登录", r.getMessage());
    }

    @Test
    @DisplayName("手机号唯一索引冲突 → 400 可读提示")
    void duplicatePhone() {
        Result<Void> r = handler.handleDuplicateKey(
                new DuplicateKeyException("Duplicate entry '13800000000' for key 't_user.uk_phone'"));
        assertEquals(400, r.getCode());
        assertEquals("该手机号已被注册", r.getMessage());
    }

    @Test
    @DisplayName("评价唯一索引冲突 → 400 可读提示")
    void duplicateEvaluation() {
        Result<Void> r = handler.handleDuplicateKey(
                new DuplicateKeyException("Duplicate entry '9-3' for key 't_evaluation.uk_order_evaluator'"));
        assertEquals(400, r.getCode());
        assertEquals("您已评价过该订单", r.getMessage());
    }

    @Test
    @DisplayName("取件码唯一索引冲突 → 400 可读提示")
    void duplicateActivePickup() {
        Result<Void> r = handler.handleDuplicateKey(
                new DuplicateKeyException("Duplicate entry 'x' for key 't_order.uk_active_pickup'"));
        assertEquals(400, r.getCode());
        assertTrue(r.getMessage().contains("取件码"));
    }

    @Test
    @DisplayName("未识别的唯一索引冲突 → 400 通用提示，不泄露 SQL 细节")
    void duplicateUnknownIndex() {
        Result<Void> r = handler.handleDuplicateKey(
                new DuplicateKeyException("Duplicate entry 'x' for key 't_wallet.uk_something_else'"));
        assertEquals(400, r.getCode());
        assertEquals("数据已存在，请勿重复提交", r.getMessage());
    }

    @Test
    @DisplayName("非唯一约束的系统异常仍走兜底 500")
    void unexpectedExceptionFallsBackTo500() {
        Result<Void> r = handler.handleException(new IllegalStateException("boom"));
        assertEquals(500, r.getCode());
    }
}

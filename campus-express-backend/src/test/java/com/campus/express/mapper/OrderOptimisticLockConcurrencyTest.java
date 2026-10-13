package com.campus.express.mapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 抢单乐观锁并发测试（规则 R-6 ~ R-10）
 *
 * <p>验证核心并发不变量：多个接单者用同一版本号同时抢同一订单，只能有且仅有 1 单成功（不超卖）。
 * 直接压测 Mapper 层的乐观锁 SQL（optimisticTakeOrder），依赖真实 MySQL（campus_express_test），
 * 是「乐观锁 + version 条件更新」这一并发控制手段的直接证据。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderOptimisticLockConcurrencyTest {

    /** 测试数据使用的固定主键区间，便于精确清理，不与真实数据冲突 */
    private static final int PUBLISHER_ID = 900000;
    private static final int TAKER_BASE = 900001;
    /** 并发抢单的接单者数量 */
    private static final int CONCURRENCY = 32;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private Integer orderId;

    @BeforeEach
    void setUp() {
        cleanup();
        // 发布者（can_publish=1）：手机号与接单者区间错开，避免 uk_phone 冲突
        insertUser(PUBLISHER_ID, "测试发布者", "18800000000", 1, 1);
        // 32 个接单者（can_publish=0）
        for (int i = 0; i < CONCURRENCY; i++) {
            int id = TAKER_BASE + i;
            insertUser(id, "测试接单者" + i, "1900000" + String.format("%04d", i), 2, 0);
        }
        // 一张待接单订单，version 初始为 0，期望取件时间设在未来（避免被超时任务扫描）
        jdbc.update("INSERT INTO t_order(publisher_id, pickup_code, express_company, pickup_location, "
                        + "deliver_location, expect_time, remark, reward_amount, version, status) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                PUBLISHER_ID, "1234", "顺丰", "菜鸟驿站A", "1号楼",
                Timestamp.valueOf(LocalDateTime.now().plusHours(3)), "并发测试包裹",
                new BigDecimal("5.00"), 0, "待接单");
        orderId = jdbc.queryForObject("SELECT order_id FROM t_order WHERE publisher_id = ?", Integer.class, PUBLISHER_ID);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    @DisplayName("32 人同版本并发抢单：有且仅有 1 单成功，订单最终归属唯一接单者")
    void onlyOneTakerWinsUnderConcurrency() throws Exception {
        final int version = 0;
        CountDownLatch ready = new CountDownLatch(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        List<Integer> winners = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);

        for (int i = 0; i < CONCURRENCY; i++) {
            final int takerId = TAKER_BASE + i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                int rows = orderMapper.optimisticTakeOrder(orderId, takerId, version);
                if (rows == 1) {
                    success.incrementAndGet();
                    winners.add(takerId);
                }
            });
        }

        // 所有线程就绪后同时放行，最大化并发竞争
        assertTrue(ready.await(10, TimeUnit.SECONDS), "线程池未能在 10s 内全部就绪");
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "并发任务未能在 30s 内结束");

        assertEquals(1, success.get(), "并发抢单必须且只能有 1 单成功（乐观锁防超卖）");
        assertEquals(1, winners.size(), "只应有唯一获胜接单者");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, version, taker_id FROM t_order WHERE order_id = ?", orderId);
        assertEquals("已接单", row.get("status"), "订单状态应流转为已接单");
        assertEquals(1, ((Number) row.get("version")).intValue(), "抢单成功后版本号应 +1");
        assertEquals(winners.get(0), ((Number) row.get("taker_id")).intValue(), "订单应归属唯一获胜者");
    }

    @Test
    @DisplayName("陈旧版本号抢单被拒绝：已接单订单无法被再次抢走")
    void staleVersionTakeIsRejected() {
        // 第一次抢单成功
        assertEquals(1, orderMapper.optimisticTakeOrder(orderId, TAKER_BASE, 0));
        // 仍用旧版本号 0 再抢：status 已非「待接单」，影响行数为 0
        assertEquals(0, orderMapper.optimisticTakeOrder(orderId, TAKER_BASE + 1, 0),
                "已接单订单不应被陈旧版本号再次抢走");
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, version, taker_id FROM t_order WHERE order_id = ?", orderId);
        assertEquals(TAKER_BASE, ((Number) row.get("taker_id")).intValue(), "订单应保持归属首个接单者");
    }

    private void insertUser(int userId, String username, String phone, int role, int canPublish) {
        jdbc.update("INSERT INTO t_user(user_id, student_no, username, phone, password_hash, role, "
                        + "credit_score, status, can_publish) VALUES (?,?,?,?,?,?,?,?,?)",
                userId, "T" + userId, username, phone, "$2a$10$testhash", role, 100, 1, canPublish);
    }

    private void cleanup() {
        jdbc.update("DELETE FROM t_order WHERE publisher_id = ?", PUBLISHER_ID);
        jdbc.update("DELETE FROM t_user WHERE user_id = ?", PUBLISHER_ID);
        for (int i = 0; i < CONCURRENCY; i++) {
            jdbc.update("DELETE FROM t_user WHERE user_id = ?", TAKER_BASE + i);
        }
    }
}
package com.campus.express.mapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 取件码并发唯一性测试（生成列 active_pickup_key + 唯一索引 uk_active_pickup）
 *
 * <p>验证数据库层的最终防线：绕过服务层预检直接写库时，同一「取件地点 + 取件码」的进行中订单
 * 仍会被唯一约束拦下；订单进入终态（已完成/已取消）后生成列置 NULL，取件码被释放可再次使用。</p>
 *
 * <p>依赖真实 MySQL（campus_express_test），是「应用层预检 + 数据库唯一约束兜底」这一
 * 双重防护手段中数据库一侧的直接证据。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderPickupCodeUniqueConstraintTest {

    /** 测试数据固定主键区间，便于精确清理，不与真实数据冲突 */
    private static final int PUBLISHER_ID = 930000;
    private static final String CODE = "9-9-9001";
    private static final String OTHER_CODE = "9-9-9002";
    private static final String LOCATION = "测试驿站A";
    private static final String OTHER_LOCATION = "测试驿站B";

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        cleanup();
        insertUser(PUBLISHER_ID, "取件码唯一测试发布者", "19300000000");
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    @DisplayName("进行中订单撞码：同一「取件地点 + 取件码」被数据库唯一约束拒绝")
    void duplicateActiveOrderRejected() {
        insertOrder(CODE, LOCATION, "待接单");
        assertThrows(DuplicateKeyException.class, () -> insertOrder(CODE, LOCATION, "取件中"),
                "进行中订单的取件码撞车必须被 uk_active_pickup 拦下");
        assertEquals(1, countActive(CODE, LOCATION), "只应存在 1 条进行中订单");
    }

    @Test
    @DisplayName("终态释放取件码：订单已取消后生成列为 NULL，同一取件码可再次发布")
    void terminalStatusReleasesPickupCode() {
        int firstOrderId = insertOrder(CODE, LOCATION, "待接单");
        assertEquals(1, jdbc.update("UPDATE t_order SET status = '已取消' WHERE order_id = ?", firstOrderId));
        assertNull(activeKeyOf(firstOrderId), "终态订单的生成列应为 NULL（取件码已释放）");

        insertOrder(CODE, LOCATION, "待接单");
        assertEquals(1, countActive(CODE, LOCATION), "释放后应能重新发布同一取件码");
    }

    @Test
    @DisplayName("终态订单不占用唯一键：多条已完成订单可持有相同取件码")
    void multipleTerminalOrdersKeepSameCode() {
        int first = insertOrder(CODE, LOCATION, "待接单");
        jdbc.update("UPDATE t_order SET status = '已完成' WHERE order_id = ?", first);
        int second = insertOrder(CODE, LOCATION, "待接单");
        jdbc.update("UPDATE t_order SET status = '已完成' WHERE order_id = ?", second);

        assertNull(activeKeyOf(first));
        assertNull(activeKeyOf(second), "唯一索引允许多个 NULL，历史订单可复用同一取件码");
    }

    @Test
    @DisplayName("不同取件地点可复用同一取件码")
    void sameCodeDifferentLocationAllowed() {
        insertOrder(CODE, LOCATION, "待接单");
        insertOrder(CODE, OTHER_LOCATION, "待接单");
        assertEquals(2, countActiveAnywhere(CODE), "取件码按「取件码 + 取件地点」组合判重");
    }

    @Test
    @DisplayName("修改订单撞码：进行中订单改码撞车同样被数据库拒绝")
    void updateToCollidingCodeRejected() {
        insertOrder(OTHER_CODE, LOCATION, "待接单");
        int secondOrderId = insertOrder(CODE, LOCATION, "待接单");

        assertThrows(DuplicateKeyException.class, () -> jdbc.update(
                "UPDATE t_order SET pickup_code = ? WHERE order_id = ?", OTHER_CODE, secondOrderId),
                "改码撞车必须被唯一约束拦下");
        assertEquals(CODE, codeOf(secondOrderId), "撞码失败后原取件码应保持不变");
    }

    private int insertOrder(String pickupCode, String pickupLocation, String status) {
        jdbc.update("INSERT INTO t_order(publisher_id, pickup_code, pickup_location, deliver_location, "
                        + "expect_time, reward_amount, version, status) VALUES (?,?,?,?,?,?,?,?)",
                PUBLISHER_ID, pickupCode, pickupLocation, "1号楼",
                Timestamp.valueOf(LocalDateTime.now().plusHours(3)), new BigDecimal("5.00"), 0, status);
        return jdbc.queryForObject("SELECT MAX(order_id) FROM t_order WHERE publisher_id = ?",
                Integer.class, PUBLISHER_ID);
    }

    private void insertUser(int userId, String username, String phone) {
        jdbc.update("INSERT INTO t_user(user_id, student_no, username, phone, password_hash, role, "
                        + "credit_score, status, can_publish) VALUES (?,?,?,?,?,?,?,?,?)",
                userId, "T" + userId, username, phone, "$2a$10$testhash", 1, 100, 1, 1);
    }

    private String activeKeyOf(int orderId) {
        return jdbc.queryForObject("SELECT active_pickup_key FROM t_order WHERE order_id = ?",
                String.class, orderId);
    }

    private String codeOf(int orderId) {
        return jdbc.queryForObject("SELECT pickup_code FROM t_order WHERE order_id = ?",
                String.class, orderId);
    }

    private int countActive(String pickupCode, String pickupLocation) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_order WHERE publisher_id = ? AND pickup_code = ? "
                        + "AND pickup_location = ? AND status NOT IN ('已完成','已取消')",
                Integer.class, PUBLISHER_ID, pickupCode, pickupLocation);
    }

    private int countActiveAnywhere(String pickupCode) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_order WHERE publisher_id = ? AND pickup_code = ? "
                        + "AND status NOT IN ('已完成','已取消')",
                Integer.class, PUBLISHER_ID, pickupCode);
    }

    private void cleanup() {
        jdbc.update("DELETE FROM t_order WHERE publisher_id = ?", PUBLISHER_ID);
        jdbc.update("DELETE FROM t_user WHERE user_id = ?", PUBLISHER_ID);
    }
}

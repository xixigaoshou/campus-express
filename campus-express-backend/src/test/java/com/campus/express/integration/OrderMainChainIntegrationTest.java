package com.campus.express.integration;

import com.campus.express.util.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单主链路端到端集成测试（MockMvc 走真实 Controller + Service + MyBatis + MySQL）
 *
 * <p>覆盖「发布需求 → 抢单接单 → 取件中 → 配送中 → 确认送达 → 确认收货」完整主链路，
 * 并断言资金闭环：发布冻结报酬、完成结算（发布者冻结释放、接单者余额入账）。
 * 与单元测试（纯 Mockito）互补——本类验证的是 HTTP 路由、JWT 拦截器、参数校验、
 * 事务边界与真实 SQL 的协同，是「整条链路能跑通」的直接证据。</p>
 *
 * <p>依赖真实测试库 campus_express_test（见 application-test.yml），与并发测试同源。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderMainChainIntegrationTest {

    /** 测试数据固定主键区间，避免与开发库真实数据冲突 */
    private static final int PUBLISHER_ID = 910001;
    private static final int TAKER_ID = 910002;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String publisherToken;
    private String takerToken;

    @BeforeEach
    void setUp() {
        cleanup();
        String hash = passwordEncoder.encode("123456");
        // 发布者：can_publish=1，已设置支付密码，钱包余额充足（发布需冻结报酬）
        insertUser(PUBLISHER_ID, "集成发布者", "18810000001", 1, 1, hash);
        // 接单者：can_publish=0，信誉分 100
        insertUser(TAKER_ID, "集成接单者", "18810000002", 2, 0, hash);
        insertWallet(PUBLISHER_ID, "1000.00");
        insertWallet(TAKER_ID, "50.00");
        publisherToken = jwtUtil.generateToken(PUBLISHER_ID, "集成发布者", 1);
        takerToken = jwtUtil.generateToken(TAKER_ID, "集成接单者", 2);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    @DisplayName("主链路：发布→抢单→取件→配送→确认送达→确认收货，订单完成且报酬结算到位")
    void fullMainChainCompletesAndSettlesReward() throws Exception {
        // ① 发布需求（冻结报酬 5 元）
        int orderId = publishOrder("9-9-9001", "8.00");
        assertEquals("待接单", orderStatus(orderId), "发布后应为待接单");
        assertEquals("992.00", publisherBalance(), "发布后可用余额应为 1000-8=992.00");
        assertEquals("8.00", publisherFrozen(), "发布后应冻结报酬 8.00");

        // ② 抢单接单
        mockMvc.perform(put("/api/order/take-order").param("orderId", String.valueOf(orderId))
                        .header("Authorization", "Bearer " + takerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals("已接单", orderStatus(orderId), "抢单后应为已接单");
        assertEquals(TAKER_ID, takerId(orderId), "订单应归属接单者");

        // ③ 取件中
        changeStatus(orderId, "取件中", takerToken);
        assertEquals("取件中", orderStatus(orderId));

        // ④ 配送中
        changeStatus(orderId, "配送中", takerToken);
        assertEquals("配送中", orderStatus(orderId));

        // ⑤ 确认送达（跳过照片）
        mockMvc.perform(put("/api/order/confirm-arrival").param("orderId", String.valueOf(orderId))
                        .param("skipPhoto", "true").header("Authorization", "Bearer " + takerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals("待收货", orderStatus(orderId), "确认送达后应为待收货");

        // ⑥ 需求方确认收货（待收货 → 已完成，触发结算）
        mockMvc.perform(put("/api/order/confirm-finish").param("orderId", String.valueOf(orderId))
                        .header("Authorization", "Bearer " + publisherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals("已完成", orderStatus(orderId), "确认收货后应为已完成");

        // 资金闭环断言：发布者冻结释放（0），接单者入账（50+8=58.00）
        assertEquals("0.00", publisherFrozen(), "完成后发布者冻结应清零");
        assertEquals("992.00", publisherBalance(), "发布者可用余额应扣减报酬 8.00");
        assertEquals("58.00", takerBalance(), "接单者应收到报酬 8.00");

        // 状态历史完整性：待接单/已接单/取件中/配送中/待收货/已完成 共 6 条
        Integer historyCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_order_status_history WHERE order_id = ?", Integer.class, orderId);
        assertEquals(6, historyCount, "主链路应写入 6 条状态历史（含发布首条）");
    }

    @Test
    @DisplayName("鉴权：未携带 JWT 访问受保护接口返回 401")
    void missingTokenIsRejectedWith401() throws Exception {
        mockMvc.perform(put("/api/order/take-order").param("orderId", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("权限：接单者角色调用发布接口被拒绝（403）")
    void takerRoleCannotPublish() throws Exception {
        String body = publishBody("9-9-9002", "5.00");
        mockMvc.perform(post("/api/order/publish").contentType(MediaType.APPLICATION_JSON)
                        .content(body).header("Authorization", "Bearer " + takerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    // ---------------- 辅助方法 ----------------

    /** 发布订单并返回订单 ID，同时断言 HTTP/业务成功 */
    private int publishOrder(String pickupCode, String reward) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/order/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishBody(pickupCode, reward))
                        .header("Authorization", "Bearer " + publisherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode node = readBody(result);
        assertNotNull(node.get("data"), "发布应返回订单 ID");
        return node.get("data").asInt();
    }

    private String publishBody(String pickupCode, String reward) {
        String expectTime = LocalDateTime.now().plusHours(3).format(TIME_FMT);
        return "{"
                + "\"pickupCode\":\"" + pickupCode + "\","
                + "\"expressCompany\":\"顺丰速运\","
                + "\"pickupLocation\":\"集成测试驿站\","
                + "\"deliverLocation\":\"9号楼901\","
                + "\"expectTime\":\"" + expectTime + "\","
                + "\"remark\":\"主链路集成测试包裹\","
                + "\"rewardAmount\":" + reward + ","
                + "\"payPassword\":\"123456\""
                + "}";
    }

    private void changeStatus(int orderId, String targetStatus, String token) throws Exception {
        String body = "{\"orderId\":" + orderId + ",\"targetStatus\":\"" + targetStatus + "\"}";
        mockMvc.perform(put("/api/order/status").contentType(MediaType.APPLICATION_JSON)
                        .content(body).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private JsonNode readBody(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json);
    }

    private String orderStatus(int orderId) {
        return jdbc.queryForObject("SELECT status FROM t_order WHERE order_id = ?", String.class, orderId);
    }

    private Integer takerId(int orderId) {
        return jdbc.queryForObject("SELECT taker_id FROM t_order WHERE order_id = ?", Integer.class, orderId);
    }

    private String publisherBalance() {
        return walletValue(PUBLISHER_ID, "balance");
    }

    private String publisherFrozen() {
        return walletValue(PUBLISHER_ID, "frozen_balance");
    }

    private String takerBalance() {
        return walletValue(TAKER_ID, "balance");
    }

    private String walletValue(int userId, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM t_wallet WHERE user_id = ?", String.class, userId);
    }

    private void insertUser(int userId, String username, String phone, int role, int canPublish, String hash) {
        jdbc.update("INSERT INTO t_user(user_id, student_no, username, phone, password_hash, role, "
                        + "credit_score, status, can_publish, pay_password_hash) VALUES (?,?,?,?,?,?,?,?,?,?)",
                userId, "IT" + userId, username, phone, hash, role, 100, 1, canPublish, hash);
    }

    private void insertWallet(int userId, String balance) {
        jdbc.update("INSERT INTO t_wallet(user_id, balance, frozen_balance) VALUES (?,?,0.00)", userId, balance);
    }

    private void cleanup() {
        // 依赖顺序：先删所有外键指向订单的子表（历史/转单/通知/留言/评价），再删订单，最后删钱包与用户
        String orderSubQuery = "(SELECT order_id FROM t_order WHERE publisher_id IN (?,?))";
        jdbc.update("DELETE FROM t_order_status_history WHERE order_id IN " + orderSubQuery, PUBLISHER_ID, TAKER_ID);
        jdbc.update("DELETE FROM t_order_transfer WHERE order_id IN " + orderSubQuery, PUBLISHER_ID, TAKER_ID);
        jdbc.update("DELETE FROM t_notification WHERE order_id IN " + orderSubQuery, PUBLISHER_ID, TAKER_ID);
        jdbc.update("DELETE FROM t_message WHERE order_id IN " + orderSubQuery, PUBLISHER_ID, TAKER_ID);
        jdbc.update("DELETE FROM t_evaluation WHERE order_id IN " + orderSubQuery, PUBLISHER_ID, TAKER_ID);
        jdbc.update("DELETE FROM t_order WHERE publisher_id IN (?,?)", PUBLISHER_ID, TAKER_ID);
        jdbc.update("DELETE FROM t_wallet_log WHERE user_id IN (?,?)", PUBLISHER_ID, TAKER_ID);
        jdbc.update("DELETE FROM t_wallet WHERE user_id IN (?,?)", PUBLISHER_ID, TAKER_ID);
        jdbc.update("DELETE FROM t_notification WHERE user_id IN (?,?)", PUBLISHER_ID, TAKER_ID);
        jdbc.update("DELETE FROM t_user WHERE user_id IN (?,?)", PUBLISHER_ID, TAKER_ID);
    }
}
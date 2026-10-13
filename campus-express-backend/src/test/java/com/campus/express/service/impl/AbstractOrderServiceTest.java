package com.campus.express.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.express.config.BusinessRulesConfig;
import com.campus.express.entity.Evaluation;
import com.campus.express.entity.Order;
import com.campus.express.entity.OrderTransfer;
import com.campus.express.entity.User;
import com.campus.express.mapper.EvaluationMapper;
import com.campus.express.mapper.OrderMapper;
import com.campus.express.mapper.OrderTransferMapper;
import com.campus.express.service.NotificationService;
import com.campus.express.service.OrderStatusHistoryService;
import com.campus.express.service.UserService;
import com.campus.express.service.WalletService;
import com.campus.express.ws.WebSocketSessionManager;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * OrderServiceImpl 单元测试基类
 *
 * <p>Mock 掉全部协作者（Mapper / 各 Service / 事务管理器 / WebSocket），用真实
 * {@link BusinessRulesConfig}（默认阈值与 application.yml 一致）构造真实的 Service，
 * 在无 Spring 容器、无数据库条件下逐一校验业务规则与并发守卫分支。</p>
 *
 * <p>订单服务已按领域拆分为 {@link OrderLifecycleServiceImpl}（流转）、
 * {@link OrderRelayServiceImpl}（接力）、{@link OrderTimeoutServiceImpl}（超时调度）三个领域服务，
 * {@link OrderServiceImpl} 退化为门面。此处用同一批 Mock 协作者构造三个领域服务再装配门面，
 * 使原有 {@code service.xxx()} 用例在委派后仍产生完全一致的 mock 交互。</p>
 *
 * <p>采用 LENIENT 严格度：基类提供通用 stub 辅助，避免每个用例都因未使用 stub 报错。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
abstract class AbstractOrderServiceTest {

    @Mock
    protected OrderMapper orderMapper;
    @Mock
    protected EvaluationMapper evaluationMapper;
    @Mock
    protected OrderTransferMapper transferMapper;
    @Mock
    protected UserService userService;
    @Mock
    protected OrderStatusHistoryService historyService;
    @Mock
    protected NotificationService notificationService;
    @Mock
    protected WalletService walletService;
    @Mock
    protected WebSocketSessionManager webSocketSessionManager;
    @Mock
    protected PlatformTransactionManager transactionManager;

    protected BusinessRulesConfig rules;
    protected OrderLifecycleServiceImpl lifecycle;
    protected OrderRelayServiceImpl relay;
    protected OrderTimeoutServiceImpl timeout;
    protected OrderServiceImpl service;

    /**
     * 预热 MyBatis-Plus 的 TableInfo / lambda 列缓存。
     *
     * <p>生产代码在拼装 {@code LambdaUpdateWrapper}/{@code LambdaQueryWrapper} 时依赖该缓存，
     * 而缓存通常由 MyBatis 加载 Mapper 时写入。纯 Mockito 单测不启动 MyBatis，
     * 若不预热会抛 {@code can not find lambda cache for this entity}。</p>
     */
    @BeforeAll
    static void warmUpMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Order.class);
        TableInfoHelper.initTableInfo(assistant, OrderTransfer.class);
        TableInfoHelper.initTableInfo(assistant, Evaluation.class);
    }

    @BeforeEach
    void initService() {
        rules = new BusinessRulesConfig();
        // 构造器参数顺序与 Lombok @RequiredArgsConstructor 生成的 final 字段顺序一致；
        // 三个领域服务共用同一批 Mock 协作者，门面再委派给它们，保证 mock 交互与拆分前一致
        relay = new OrderRelayServiceImpl(orderMapper, transferMapper, userService,
                historyService, notificationService, webSocketSessionManager, rules);
        timeout = new OrderTimeoutServiceImpl(orderMapper, userService, historyService,
                notificationService, walletService, rules, transactionManager, relay);
        lifecycle = new OrderLifecycleServiceImpl(orderMapper, evaluationMapper, userService,
                historyService, notificationService, walletService, webSocketSessionManager, rules);
        service = new OrderServiceImpl(lifecycle, relay, timeout);
    }

    /** 构造测试用户：信誉分与角色可控 */
    protected User user(int id, String name, int credit, boolean canPublish) {
        User u = new User();
        u.setUserId(id);
        u.setUsername(name);
        u.setCreditScore(credit);
        u.setCanPublish(canPublish);
        return u;
    }

    /** 构造测试订单：默认 version=0、报酬 5 元、期望取件时间在未来 2 小时 */
    protected Order order(int id, String status, int publisherId, Integer takerId) {
        Order o = new Order();
        o.setOrderId(id);
        o.setStatus(status);
        o.setPublisherId(publisherId);
        o.setTakerId(takerId);
        o.setVersion(0);
        o.setRewardAmount(new BigDecimal("5.00"));
        o.setExpressCompany("顺丰");
        o.setPickupCode("1234");
        o.setPickupLocation("菜鸟驿站A");
        o.setExpectTime(LocalDateTime.now().plusHours(2));
        return o;
    }
}
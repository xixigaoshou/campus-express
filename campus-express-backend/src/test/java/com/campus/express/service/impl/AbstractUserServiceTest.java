package com.campus.express.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.express.config.BusinessRulesConfig;
import com.campus.express.config.JwtInterceptor;
import com.campus.express.entity.CreditLog;
import com.campus.express.entity.Order;
import com.campus.express.entity.User;
import com.campus.express.mapper.CreditLogMapper;
import com.campus.express.mapper.OrderMapper;
import com.campus.express.mapper.UserMapper;
import com.campus.express.mapper.WalletMapper;
import com.campus.express.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.Mockito.when;

/**
 * UserServiceImpl 单元测试基类
 *
 * <p>Mock 掉全部协作者（4 个 Mapper / 密码编码器 / JWT 工具 / 请求上下文），用真实
 * {@link BusinessRulesConfig} 构造 Service，在无 Spring 容器、无数据库条件下校验
 * 注册登录、密码维护、支付密码锁定、信誉分封顶、短信验证码限流、找回密码限次等安全与业务规则。</p>
 *
 * <p>采用 LENIENT 严格度：基类提供通用 stub 辅助，避免每个用例都因未使用 stub 报错。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
abstract class AbstractUserServiceTest {

    @Mock
    protected UserMapper userMapper;
    @Mock
    protected CreditLogMapper creditLogMapper;
    @Mock
    protected WalletMapper walletMapper;
    @Mock
    protected OrderMapper orderMapper;
    @Mock
    protected PasswordEncoder passwordEncoder;
    @Mock
    protected JwtUtil jwtUtil;
    @Mock
    protected HttpServletRequest request;

    protected BusinessRulesConfig rules;
    protected UserServiceImpl service;

    /**
     * 预热 MyBatis-Plus 的 TableInfo / lambda 列缓存。
     * 生产代码拼装 {@code LambdaQueryWrapper} 时依赖该缓存，纯 Mockito 单测不启动 MyBatis，
     * 若不预热会抛 {@code can not find lambda cache for this entity}。
     */
    @BeforeAll
    static void warmUpMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, User.class);
        TableInfoHelper.initTableInfo(assistant, CreditLog.class);
        TableInfoHelper.initTableInfo(assistant, Order.class);
    }

    @BeforeEach
    void initService() {
        rules = new BusinessRulesConfig();
        // 构造器参数顺序与 Lombok @RequiredArgsConstructor 生成的 final 字段顺序一致
        service = new UserServiceImpl(userMapper, creditLogMapper, walletMapper, orderMapper,
                passwordEncoder, jwtUtil, request, rules);
    }

    /** 构造测试用户：信誉分、状态可控，默认角色需求方 */
    protected User user(int id, String name, int credit, int status) {
        User u = new User();
        u.setUserId(id);
        u.setUsername(name);
        u.setCreditScore(credit);
        u.setStatus(status);
        u.setRole(1);
        return u;
    }

    /** 模拟已登录：向请求上下文注入当前登录用户 ID */
    protected void loggedInAs(int userId) {
        when(request.getAttribute(JwtInterceptor.ATTR_USER_ID)).thenReturn(userId);
    }
}
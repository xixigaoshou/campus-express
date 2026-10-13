package com.campus.express.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.express.entity.Wallet;
import com.campus.express.entity.WalletLog;
import com.campus.express.mapper.WalletLogMapper;
import com.campus.express.mapper.WalletMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

/**
 * WalletServiceImpl 单元测试基类
 *
 * <p>Mock 掉 {@link WalletMapper} / {@link WalletLogMapper} 两个协作者，在无 Spring 容器、
 * 无数据库条件下逐一校验资金链路的业务规则：余额校验、原子扣减兜底、订单维度净冻结幂等、
 * 结算幂等与差额补冻结等。</p>
 *
 * <p>采用 LENIENT 严格度：基类提供通用 stub 辅助，避免每个用例都因未使用 stub 报错。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
abstract class AbstractWalletServiceTest {

    @Mock
    protected WalletMapper walletMapper;
    @Mock
    protected WalletLogMapper walletLogMapper;

    protected WalletServiceImpl service;

    /**
     * 预热 MyBatis-Plus 的 TableInfo / lambda 列缓存。
     * 生产代码拼装 {@code LambdaQueryWrapper} 时依赖该缓存，纯 Mockito 单测不启动 MyBatis，
     * 若不预热会抛 {@code can not find lambda cache for this entity}。
     */
    @BeforeAll
    static void warmUpMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Wallet.class);
        TableInfoHelper.initTableInfo(assistant, WalletLog.class);
    }

    @BeforeEach
    void initService() {
        service = new WalletServiceImpl(walletMapper, walletLogMapper);
    }

    /** 构造测试钱包：可用余额与冻结余额可控 */
    protected Wallet wallet(int userId, String balance, String frozen) {
        Wallet w = new Wallet();
        w.setUserId(userId);
        w.setBalance(new BigDecimal(balance));
        w.setFrozenBalance(new BigDecimal(frozen));
        return w;
    }

    /** 构造测试流水：类型与金额可控 */
    protected WalletLog log(int logType, String amount) {
        WalletLog l = new WalletLog();
        l.setLogType(logType);
        l.setAmount(new BigDecimal(amount));
        return l;
    }
}
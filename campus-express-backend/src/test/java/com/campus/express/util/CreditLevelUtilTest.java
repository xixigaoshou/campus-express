package com.campus.express.util;

import com.campus.express.config.BusinessRulesConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 信誉等级与并发上限计算单元测试（规则 R-24 / R-9）
 * 纯函数逻辑，不依赖 Spring 容器与数据库
 */
class CreditLevelUtilTest {

    /** 与 application.yml 中 business 节点默认值一致 */
    private final BusinessRulesConfig cfg = new BusinessRulesConfig();

    @Test
    @DisplayName("等级划分：80/100/120 为三段分界点")
    void levelBoundaries() {
        assertEquals(CreditLevelUtil.NORMAL, CreditLevelUtil.levelOf(80, cfg));
        assertEquals(CreditLevelUtil.NORMAL, CreditLevelUtil.levelOf(99, cfg));
        assertEquals(CreditLevelUtil.GOOD, CreditLevelUtil.levelOf(100, cfg));
        assertEquals(CreditLevelUtil.GOOD, CreditLevelUtil.levelOf(119, cfg));
        assertEquals(CreditLevelUtil.EXCELLENT, CreditLevelUtil.levelOf(120, cfg));
        assertEquals(CreditLevelUtil.EXCELLENT, CreditLevelUtil.levelOf(150, cfg));
    }

    @Test
    @DisplayName("低于普通下限（<80）仍归为普通，不抛异常")
    void belowNormalFloorStillNormal() {
        assertEquals(CreditLevelUtil.NORMAL, CreditLevelUtil.levelOf(0, cfg));
        assertEquals(CreditLevelUtil.NORMAL, CreditLevelUtil.levelOf(79, cfg));
    }

    @Test
    @DisplayName("同时进行中订单上限：普通2 / 良好3 / 优秀5")
    void concurrentLimitByLevel() {
        assertEquals(2, CreditLevelUtil.concurrentLimit(90, cfg));
        assertEquals(3, CreditLevelUtil.concurrentLimit(110, cfg));
        assertEquals(5, CreditLevelUtil.concurrentLimit(130, cfg));
    }
}
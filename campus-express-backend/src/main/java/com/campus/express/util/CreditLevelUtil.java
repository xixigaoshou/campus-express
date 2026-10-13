package com.campus.express.util;

import com.campus.express.config.BusinessRulesConfig;

/**
 * 信誉等级计算工具（规则 R-24）
 * 等级按信誉分区间划分：普通(80-99) / 良好(100-119) / 优秀(120+)
 * 等级决定同时进行中订单上限（R-9）
 */
public final class CreditLevelUtil {

    private CreditLevelUtil() {
    }

    /** 等级名 */
    public static final String NORMAL = "普通";
    public static final String GOOD = "良好";
    public static final String EXCELLENT = "优秀";

    /** 按信誉分返回等级名（R-24） */
    public static String levelOf(int creditScore, BusinessRulesConfig cfg) {
        if (creditScore >= cfg.getCredit().getLevelExcellentMin()) {
            return EXCELLENT;
        }
        if (creditScore >= cfg.getCredit().getLevelGoodMin()) {
            return GOOD;
        }
        return NORMAL;
    }

    /** 按信誉分返回可同时进行中的订单上限（R-9） */
    public static int concurrentLimit(int creditScore, BusinessRulesConfig cfg) {
        return switch (levelOf(creditScore, cfg)) {
            case EXCELLENT -> cfg.getConcurrentOrderLimit().getExcellent();
            case GOOD -> cfg.getConcurrentOrderLimit().getGood();
            default -> cfg.getConcurrentOrderLimit().getNormal();
        };
    }
}

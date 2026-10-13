package com.campus.express.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 业务规则参数配置绑定（对应 application.yml 的 business 节点）
 * 对应《需求分析报告》第 6 章业务规则 R-9 / R-14 ~ R-24，参数化支持后续调整
 */
@Data
@Component
@ConfigurationProperties(prefix = "business")
public class BusinessRulesConfig {

    /** 信誉分规则（R-21 ~ R-24） */
    private Credit credit = new Credit();

    /** 同时进行中订单上限（R-9，按信誉等级） */
    private ConcurrentOrderLimit concurrentOrderLimit = new ConcurrentOrderLimit();

    /** 超时规则（R-14 ~ R-16） */
    private Timeout timeout = new Timeout();

    /** 报酬规则（R-17） */
    private Reward reward = new Reward();

    /** 评价有效期（R-21） */
    private Evaluation evaluation = new Evaluation();

    /** 转单接力规则 */
    private Relay relay = new Relay();

    /** 发布者取消频控（反刷单） */
    private Cancel cancel = new Cancel();

    /** 找回密码安全策略（免登录流程） */
    private PasswordReset passwordReset = new PasswordReset();

    /** 注册短信验证码策略（手机号真实性校验） */
    private Sms sms = new Sms();

    @Data
    public static class Credit {
        private Integer initScore = 100;          // 初始信誉分
        private Integer maxScore = 150;           // 信誉分上限：达到后好评不再加分
        private Integer goodStar = 4;             // 星级 >=4：好评
        private Integer goodAdd = 1;              // 好评加分数
        private Integer badStar = 3;              // 星级 <=3：差评
        private Integer badDeduct = 2;            // 差评扣分数
        private Integer breachDeduct = 5;         // 接单者违约（接单后取消 / 超时未取件 / 取件后超时未送达）扣分数
        private Integer banThreshold = 80;        // 低于该值禁止接单
        private Integer levelNormalMin = 80;      // 普通下限
        private Integer levelGoodMin = 100;       // 良好下限
        private Integer levelExcellentMin = 120;  // 优秀下限
    }

    @Data
    public static class ConcurrentOrderLimit {
        private Integer normal = 2;
        private Integer good = 3;
        private Integer excellent = 5;
    }

    @Data
    public static class Timeout {
        private Integer waitAcceptHours = 24;    // 发布 24h 无人接单提醒
        private Integer pickupOverdueMinutes = 30; // 超过期望取件时间 30 分钟仍未取件：自动取消
        private Integer pickingOverdueHours = 6;   // 取件中/配送中超过期望取件时间 6 小时仍未送达：自动取消
        private Integer confirmReceiptHours = 24;  // 确认送达后 24h 未确认收货：系统自动确认收货
    }

    @Data
    public static class Reward {
        private java.math.BigDecimal min = new java.math.BigDecimal("2.0");
        private java.math.BigDecimal max = new java.math.BigDecimal("50.0");
    }

    @Data
    public static class Evaluation {
        private Integer validDays = 7;           // 评价有效期：完成后 7 天内
    }

    @Data
    public static class Relay {
        private Integer timeoutMinutes = 30;     // 发起接力后 30 分钟无人接力：自动撤回，原接单者继续履约
        private Integer minCredit = 90;          // 转单接力权限门槛：信誉分低于该值不可发起转单
        private Integer weeklyLimit = 3;         // 每人每周最多发起转单接力次数（自然周，周一 00:00 重置）
    }

    @Data
    public static class Cancel {
        private Integer dailyLimit = 3;          // 发布者每个自然日最多取消待接单订单次数，达上限后当日禁止再发布（反刷单）
    }

    @Data
    public static class PasswordReset {
        private Integer tokenMinutes = 10;           // 身份验证通过后签发的一次性重置令牌有效期（分钟）
        private Integer maxAttempts = 5;             // 同一账号在时间窗内允许的身份验证尝试次数
        private Integer attemptWindowMinutes = 15;   // 尝试次数统计时间窗（分钟）
    }

    @Data
    public static class Sms {
        private Integer codeMinutes = 5;             // 短信验证码有效期（分钟）
        private Integer resendSeconds = 60;          // 同一手机号两次发送的最小间隔（秒），防刷
        private Integer dailyLimit = 10;             // 同一手机号每个自然日最多发送次数
        private Integer maxVerifyAttempts = 5;       // 同一手机号验证码最多校验失败次数（超过作废）
        // 演示开关：true 时发送接口在响应中回传验证码（无真实短信网关，便于本地演示）；
        // 生产环境应置为 false，验证码仅通过短信通道下发，不得随接口返回
        private Boolean exposeCode = true;
    }
}

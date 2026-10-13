package com.campus.express.common;

import java.util.List;

/**
 * 转单接力原因清单（接单者在取件前无法继续履约时可选）
 * 与取消原因区分：转单不扣信誉分，订单由其他接单者接力完成，原接单者不再履约
 * 原因文案写入 t_order.transfer_reason 字段；后端按清单校验，防止随意填写
 */
public final class TransferReason {

    private TransferReason() {
    }

    /** 原因项：reason 为存储/展示文案，desc 为补充说明 */
    public record Item(String reason, String desc) {
    }

    public static final List<Item> REASONS = List.of(
            new Item("临时有事/时间冲突", "突然有课、考试或急事，无法按时取件"),
            new Item("同时接单太多忙不过来", "手上订单较多，时间排不开"),
            new Item("身体原因/受伤", "生病或受伤，暂时无法跑动"),
            new Item("天气原因/下雨等", "暴雨、高温等不便出行"),
            new Item("距离太远/不顺路", "取件点或送达点较远，路程不划算"),
            new Item("驿站关门/无法取件", "到达时驿站已关门或快递柜故障"),
            new Item("包裹太大/太重拿不了", "超出预期，一个人拿不动"),
            new Item("其他原因", "兜底选项"));
    /** 校验原因是否在清单内 */
    public static boolean isValid(String reason) {
        return reason != null && REASONS.stream().anyMatch(i -> i.reason().equals(reason));
    }
}
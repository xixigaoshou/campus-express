package com.campus.express.common;

import java.util.List;

/**
 * 取消原因清单（用户主动取消时必选，规则 R-2 / R-12 扩展）
 * 需求方（发布者）与代取者（接单者）各自独立的原因选项，后端按角色校验防止随意填写
 * 原因文案写入 t_order.cancel_reason 字段；编号仅为前端展示序号，不参与存储
 */
public final class CancelReason {

    private CancelReason() {
    }

    /** 需求方（发布者）取消原因：待接单订单由发布者取消时必选其一 */
    public static final List<String> PUBLISHER_REASONS = List.of(
            "自己已取/不需要了",
            "发错信息/下错单",
            "等太久没人接",
            "临时改主意",
            "联系不上接单者",
            "协商一致取消",
            "快递被退回/已签收",
            "取件时间对不上",
            "报酬填错了",
            "其他原因");

    /** 代取者（接单者）取消原因：已接单/取件中由接单者取消时必选其一 */
    public static final List<String> TAKER_REASONS = List.of(
            "联系不上需求方",
            "驿站关门/取件点关闭",
            "快递柜故障/无法取件",
            "包裹找不到/已被取走",
            "包裹太大/太重拿不了",
            "宿舍楼进不去/太难找",
            "临时有事/时间冲突",
            "同时接单太多忙不过来",
            "身体原因/受伤",
            "天气原因/下雨等",
            "报酬太低/不划算",
            "需求方要求过多/难沟通",
            "其他原因");
}

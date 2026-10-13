package com.campus.express.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.campus.express.entity.Order;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单视图对象：订单字段 + 关联用户昵称（用户关联查询技术点）
 * 需求大厅 / 订单详情 / 个人订单中心展示用
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderVO extends Order {

    /** 发布者昵称（JOIN t_user 关联查询） */
    private String publisherName;

    /** 接单者昵称（未接单前为 null） */
    private String takerName;

    /** 接单者信誉分（未接单前为 null；规则 R-24 需求详情页展示信誉等级） */
    private Integer takerCreditScore;

    /** 当前登录用户是否已评价该订单（仅我的订单列表填充，用于展示「去评价」入口） */
    @TableField(exist = false)
    private Boolean myEvaluated;
}

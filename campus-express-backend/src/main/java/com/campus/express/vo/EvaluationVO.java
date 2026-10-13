package com.campus.express.vo;

import com.campus.express.entity.Evaluation;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评价视图对象：评价字段 + 双方昵称（用户关联查询技术点）
 * 订单详情页评价展示用
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EvaluationVO extends Evaluation {

    /** 评价者昵称（JOIN t_user 关联查询） */
    private String evaluatorName;

    /** 被评价者昵称 */
    private String evaluateeName;

    /** 关联订单快递公司（JOIN t_order 关联查询，评价记录列表展示用） */
    private String expressCompany;

    /** 关联订单取件码 */
    private String pickupCode;

    /** 关联订单报酬金额 */
    private java.math.BigDecimal rewardAmount;
}

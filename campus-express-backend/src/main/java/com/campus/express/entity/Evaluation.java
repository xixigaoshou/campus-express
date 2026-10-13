package com.campus.express.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评价记录实体（对应表 t_evaluation）
 * 订单完成后双方互评数据，用于信誉分计算（规则 R-21 ~ R-24）
 * 字段与数据库表严格一一对应，禁止增减
 */
@Data
@TableName("t_evaluation")
public class Evaluation {

    /** 评价唯一标识，自增主键 */
    @TableId(type = IdType.AUTO)
    private Integer evaluationId;

    /** 订单 ID，关联 t_order */
    private Integer orderId;

    /** 评价者用户 ID */
    private Integer evaluatorId;

    /** 被评价者用户 ID */
    private Integer evaluateeId;

    /** 满意度星级（1-5）：4 星及以上好评 +1 分，3 星及以下差评 -2 分（R-22） */
    private Integer starLevel;

    /** 文字评价内容 */
    private String content;

    /** 评价时间 */
    private LocalDateTime createTime;
}

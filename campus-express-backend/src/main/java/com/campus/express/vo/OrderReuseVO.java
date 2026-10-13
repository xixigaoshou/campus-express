package com.campus.express.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 再次发布模板（一键复制历史订单预填发布表单）
 * 仅订单发布者本人可获取；期望取件时间不预填，重新发布时应重新选择
 */
@Data
public class OrderReuseVO {

    /** 取件码（可空） */
    private String pickupCode;

    /** 快递公司 */
    private String expressCompany;

    /** 取件地点 */
    private String pickupLocation;

    /** 送达地点 */
    private String deliverLocation;

    /** 包裹备注（可空） */
    private String remark;

    /** 报酬金额（原订单值，用户可修改） */
    private BigDecimal rewardAmount;
}

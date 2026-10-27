package com.campus.express.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 代取需求订单实体（对应表 t_order）
 * 系统主业务表；version 为乐观锁版本号，用于抢单并发控制（规则 R-7）
 * 字段与数据库表严格一一对应，禁止增减
 */
@Data
@TableName("t_order")
public class Order {

    /** 订单唯一标识，自增主键 */
    @TableId(type = IdType.AUTO)
    private Integer orderId;

    /** 发布者用户 ID，关联 t_user */
    private Integer publisherId;

    /** 接单者用户 ID，未接单前为空 */
    private Integer takerId;

    /** 取件码（同一取件地点内，进行中订单唯一） */
    private String pickupCode;

    /** 快递公司 */
    private String expressCompany;

    /** 取件地点 */
    private String pickupLocation;

    /** 送达地点 */
    private String deliverLocation;

    /** 期望取件时间 */
    private LocalDateTime expectTime;

    /** 包裹备注 */
    private String remark;

    /** 报酬金额（规则 R-17：2 ~ 50 元） */
    private BigDecimal rewardAmount;

    /**
     * 乐观锁版本号，初始为 0。
     * 抢单时以 status='待接单' AND version=客户端版本 为更新条件，
     * 影响行数为 0 即抢单失败（MyBatis-Plus 乐观锁插件自动拼接 version 条件并 +1）
     */
    @Version
    private Integer version;

    /** 订单状态：待接单/已接单/取件中/配送中/待收货/已完成/已取消（状态机见规则 R-1 ~ R-5） */
    private String status;

    /** 发布时间（插入时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 最近更新时间（插入/更新时自动填充）。
     * 必须由 MetaObjectHandler 无条件覆盖：updateById 会把实体里从库里读出的旧值一并 SET 回去，
     * 而 MySQL 的 ON UPDATE CURRENT_TIMESTAMP 只在未显式赋值时生效，导致更新时间不前进
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 接单者确认送达时间（配送中 → 待收货时记录，24 小时自动确认收货的计时起点） */
    private LocalDateTime confirmArrivalTime;

    /** 接单者确认取件时间（已接单 → 取件中时记录，取件后送达超时的计时起点） */
    private LocalDateTime pickupTime;

    /** 取消原因（发布者取消/接单者取消/超时自动取消），订单重新被接单时清空 */
    private String cancelReason;

    /** 取件照片相对路径（接单者取件凭证，选填） */
    private String pickupPhoto;

    /** 送达照片相对路径（接单者送达凭证，必填，无法拍照可跳过） */
    private String deliverPhoto;

    /** 转单接力状态：NULL=无接力 / 待接力（见 TransferStatus），不改变订单状态机 */
    private String transferStatus;

    /** 转单原因（接力中展示，接力完成/撤回/过期后清空） */
    private String transferReason;
}

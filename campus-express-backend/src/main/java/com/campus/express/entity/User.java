package com.campus.express.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体（对应表 t_user）
 * 数据库列与实体属性一一对应；payPasswordSet 为非持久化派生字段（@TableField(exist = false)）
 */
@Data
@TableName("t_user")
public class User {

    /** 用户唯一标识，自增主键 */
    @TableId(type = IdType.AUTO)
    private Integer userId;

    /** 学号/工号，唯一 */
    private String studentNo;

    /** 用户姓名 */
    private String username;

    /** 手机号，唯一 */
    private String phone;

    /** 加密后的密码（BCrypt 加盐哈希，禁止明文存储） */
    private String passwordHash;

    /** 角色：1 需求方 2 接单方（同一账号可参与两侧业务） */
    private Integer role;

    /** 宿舍区域，用于送达地点匹配 */
    private String dormArea;

    /** 信誉分值，默认 100（规则 R-22/R-23 动态调整） */
    private Integer creditScore;

    /** 账户状态：1 正常 0 禁用 */
    private Integer status;

    /** 发布权限：true=发布者（可发布需求，不可抢单） false=接单者（可抢单，不可发布） */
    private Boolean canPublish;

    /** 头像地址（相对路径 /uploads/avatar/xxx），空则前端显示姓名占位 */
    private String avatar;

    /** 支付密码哈希（BCrypt 加盐，禁止明文）；空表示尚未设置，充值/提现/付钱前须先设置 */
    private String payPasswordHash;

    /** 是否已设置支付密码（非数据库列，返回给前端判断是否需要引导设置） */
    @TableField(exist = false)
    private Boolean payPasswordSet;

    /** 注册时间 */
    private LocalDateTime createTime;
}

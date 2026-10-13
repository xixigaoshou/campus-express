package com.campus.express.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.express.dto.ChangePasswordDTO;
import com.campus.express.dto.LoginDTO;
import com.campus.express.dto.RegisterDTO;
import com.campus.express.dto.ResetPasswordCodeDTO;
import com.campus.express.dto.ResetPasswordDTO;
import com.campus.express.dto.SetPayPasswordDTO;
import com.campus.express.dto.UpdateUserDTO;
import com.campus.express.entity.CreditLog;
import com.campus.express.entity.User;
import com.campus.express.vo.UserStatsVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 用户服务：注册 / 登录 / 当前用户 / 资料维护 / 密码维护 / 信誉分（业务规则 R-22 ~ R-24）
 */
public interface UserService {

    /**
     * 注册（R：密码 BCrypt 加盐加密，禁止明文；初始化信誉分 100）
     * 须先通过 sendRegisterCode 获取并携带手机短信验证码（校验手机号真实性）
     */
    void register(RegisterDTO dto);

    /**
     * 发送注册短信验证码（免登录）：为指定手机号生成 6 位验证码
     * 安全策略：验证码带时效（默认 5 分钟）、同号发送间隔限制、每日发送次数上限
     *
     * @param phone 接收验证码的手机号
     * @return 验证码明文——仅在演示开关 business.sms.expose-code=true 时返回，否则返回 null（生产走短信通道）
     */
    String sendRegisterCode(String phone);

    /**
     * 登录：学号/手机号 + 密码比对，签发 JWT 令牌
     *
     * @return { token, user }
     */
    Map<String, Object> login(LoginDTO dto);

    /**
     * 获取当前登录用户（未登录抛出业务异常 401）
     */
    User getCurrentUser();

    /**
     * 更新个人资料：仅允许改宿舍区域与手机号，学号不可修改
     */
    void updateUserInfo(UpdateUserDTO dto);

    /**
     * 修改密码（登录状态）：校验原密码正确后更新为新密码 BCrypt 哈希
     */
    void changePassword(ChangePasswordDTO dto);

    /**
     * 设置 / 修改支付密码（登录状态）：首次设置无需原支付密码，已设置后修改须校验原支付密码；
     * 支付密码为 6 位数字，BCrypt 加盐存储，不得与登录密码相同
     */
    void setPayPassword(SetPayPasswordDTO dto);

    /**
     * 校验支付密码（充值 / 提现 / 付钱等资金操作前调用）
     * 未设置支付密码时抛出业务异常，引导用户先到个人中心设置
     */
    void verifyPayPassword(Integer userId, String rawPayPassword);

    /**
     * 找回密码第一步（免登录）：校验学号 + 注册手机号是否匹配，通过后向该手机号下发短信验证码
     * 安全策略：时间窗内限制身份验证尝试次数，防止暴力枚举学号 + 手机号；
     * 发送复用短信间隔与每日次数限制
     *
     * @return 验证码明文——仅在演示开关 business.sms.expose-code=true 时返回，否则返回 null
     */
    String sendResetCode(ResetPasswordCodeDTO dto);

    /**
     * 找回密码第二步（免登录）：校验下发到注册手机号的短信验证码（证明手机号归属）后重置密码
     */
    void resetPassword(ResetPasswordDTO dto);

    /**
     * 信誉分增减（R-22）：score + delta，结果不得低于 0；同时写入信誉分明细流水（t_credit_log）
     * 低于 80 分标记接单受限（R-23，由 OrderService 在接单时校验）
     *
     * @param userId         目标用户
     * @param delta          变化量：好评 +1 / 差评 -2 / 恶意取消 -5
     * @param changeType     变化类型（展示用）：评价好评/评价差评/接单者取消/接单超时
     * @param remark         流水说明（含订单号，便于追溯）
     * @param relatedOrderId 关联订单ID，可空
     */
    void updateCreditScore(Integer userId, int delta, String changeType, String remark, Integer relatedOrderId);

    /**
     * 分页查询当前登录用户自己的信誉分明细（倒序，最新在前）
     */
    IPage<CreditLog> pageCreditLogs(int pageNum, int pageSize);

    /**
     * 个人中心统计（全量聚合）：发布总数/取消数、接单完成数/取消数、累计收入/支出
     * 由后端 COUNT / SUM 聚合，避免前端按分页上限统计导致口径失真
     */
    UserStatsVO getUserStats();

    /**
     * 上传并更新当前用户头像
     * 校验：仅图片格式（jpg/png/gif/webp）、大小不超过 5MB；保存至 uploads/avatar/ 后返回可访问相对路径
     */
    String updateAvatar(MultipartFile file);

    /**
     * 按 ID 查询用户
     */
    User getById(Integer userId);

    /**
     * 计算用户信誉等级（R-24）：普通/良好/优秀
     */
    String creditLevel(User user);
}

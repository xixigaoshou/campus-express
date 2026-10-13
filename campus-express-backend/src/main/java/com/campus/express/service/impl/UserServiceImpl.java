package com.campus.express.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.express.common.BusinessException;
import com.campus.express.common.OrderStatus;
import com.campus.express.config.BusinessRulesConfig;
import com.campus.express.config.JwtInterceptor;
import com.campus.express.dto.ChangePasswordDTO;
import com.campus.express.dto.LoginDTO;
import com.campus.express.dto.RegisterDTO;
import com.campus.express.dto.ResetPasswordCodeDTO;
import com.campus.express.dto.ResetPasswordDTO;
import com.campus.express.dto.SetPayPasswordDTO;
import com.campus.express.dto.UpdateUserDTO;
import com.campus.express.entity.CreditLog;
import com.campus.express.entity.Order;
import com.campus.express.entity.User;
import com.campus.express.entity.Wallet;
import com.campus.express.mapper.CreditLogMapper;
import com.campus.express.mapper.OrderMapper;
import com.campus.express.mapper.UserMapper;
import com.campus.express.mapper.WalletMapper;
import com.campus.express.service.UserService;
import com.campus.express.util.CreditLevelUtil;
import com.campus.express.util.ImageUtil;
import com.campus.express.util.JwtUtil;
import com.campus.express.vo.UserStatsVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户服务实现
 * 业务规则：R-22 信誉分动态计算 / R-23 低于阈值禁止接单 / R-24 信誉等级
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final CreditLogMapper creditLogMapper;
    private final WalletMapper walletMapper;
    private final OrderMapper orderMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final HttpServletRequest request;
    private final BusinessRulesConfig rules;

    /** 找回密码身份验证尝试记录（key=学号），用于时间窗内限次，防止暴力枚举学号+手机号 */
    private final Map<String, ResetAttempt> resetAttempts = new ConcurrentHashMap<>();

    /** 验证尝试记录：时间窗起点与窗内失败次数 */
    private static class ResetAttempt {
        private int count;
        private LocalDateTime windowStart;
    }

    /** 支付密码连续错误次数上限，达到后锁定 */
    private static final int PAY_PWD_MAX_ATTEMPTS = 5;

    /** 支付密码锁定时长（分钟） */
    private static final int PAY_PWD_LOCK_MINUTES = 15;

    /** 支付密码错误记录（key=userId）：6 位纯数字仅 100 万组合，须限次防脚本暴力枚举 */
    private final Map<Integer, PayPwdAttempt> payPwdAttempts = new ConcurrentHashMap<>();

    /** 支付密码错误记录：连续失败次数与锁定截止时间 */
    private static class PayPwdAttempt {
        private int count;
        private LocalDateTime lockUntil;
    }

    /** 短信验证码（内存存储，key=场景:手机号）；单实例部署足够，多实例应替换为 Redis 等共享存储 */
    private final Map<String, SmsCodeEntry> smsCodes = new ConcurrentHashMap<>();

    /** 短信发送记录（key=场景:手机号），用于发送间隔与每日次数限制，防止短信接口被刷 */
    private final Map<String, SmsSendLog> smsSendLogs = new ConcurrentHashMap<>();

    /** 短信验证码载体：验证码、过期时间、已失败校验次数 */
    private static class SmsCodeEntry {
        private final String code;
        private final LocalDateTime expireAt;
        private int failCount;

        private SmsCodeEntry(String code, LocalDateTime expireAt) {
            this.code = code;
            this.expireAt = expireAt;
        }
    }

    /** 发送记录：上次发送时间、统计日期与当日已发送次数 */
    private static class SmsSendLog {
        private LocalDateTime lastSendAt;
        private LocalDate day;
        private int dayCount;
    }

    /** 场景化 key：不同业务（注册/找回密码）验证码互相隔离，不可跨场景复用 */
    private static String smsKey(String scene, String phone) {
        return scene + ":" + (phone == null ? "" : phone.trim());
    }

    /**
     * 发送短信验证码（内部通用）：为指定手机号生成 6 位验证码
     * 安全策略：同号发送间隔限制（防刷）、每日发送次数上限、验证码带时效
     * 说明：无真实短信网关，演示模式下通过 business.sms.expose-code=true 在响应中回传验证码
     *
     * @param scene      业务场景（register / reset），用于隔离不同用途的验证码
     * @param sceneLabel 场景中文名（仅日志展示）
     */
    private String sendSmsCode(String scene, String sceneLabel, String phone) {
        String normalized = phone == null ? "" : phone.trim();
        String key = smsKey(scene, normalized);
        SmsSendLog sendLog = smsSendLogs.computeIfAbsent(key, k -> new SmsSendLog());
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        // 跨天重置当日发送计数
        if (!today.equals(sendLog.day)) {
            sendLog.day = today;
            sendLog.dayCount = 0;
        }
        if (sendLog.dayCount >= rules.getSms().getDailyLimit()) {
            throw new BusinessException("今日验证码发送次数已达上限（" + rules.getSms().getDailyLimit() + "次），请明日再试");
        }
        // 发送间隔限制：同一手机号两次发送需间隔 resendSeconds 秒
        if (sendLog.lastSendAt != null
                && sendLog.lastSendAt.plusSeconds(rules.getSms().getResendSeconds()).isAfter(now)) {
            long wait = java.time.Duration.between(now,
                    sendLog.lastSendAt.plusSeconds(rules.getSms().getResendSeconds())).getSeconds();
            throw new BusinessException("验证码发送过于频繁，请 " + wait + " 秒后再试");
        }
        // 生成 6 位随机验证码（SecureRandom 防止可预测）
        String code = String.format("%06d", new SecureRandom().nextInt(1000000));
        smsCodes.put(key, new SmsCodeEntry(code, now.plusMinutes(rules.getSms().getCodeMinutes())));
        sendLog.lastSendAt = now;
        sendLog.dayCount++;
        log.info("[{}验证码] 手机号 {} 生成验证码 {}，有效期 {} 分钟",
                sceneLabel, normalized, code, rules.getSms().getCodeMinutes());
        return Boolean.TRUE.equals(rules.getSms().getExposeCode()) ? code : null;
    }

    /** 校验短信验证码：存在、未过期、正确；错误累计达上限则作废；校验通过后一次性失效 */
    private void verifySmsCode(String scene, String phone, String code) {
        String key = smsKey(scene, phone);
        SmsCodeEntry entry = smsCodes.get(key);
        if (entry == null) {
            throw new BusinessException("请先获取短信验证码");
        }
        if (entry.expireAt.isBefore(LocalDateTime.now())) {
            smsCodes.remove(key);
            throw new BusinessException("短信验证码已过期，请重新获取");
        }
        if (!entry.code.equals(code)) {
            entry.failCount++;
            if (entry.failCount >= rules.getSms().getMaxVerifyAttempts()) {
                smsCodes.remove(key);
                throw new BusinessException("验证码错误次数过多，验证码已作废，请重新获取");
            }
            throw new BusinessException("短信验证码不正确");
        }
        // 一次性：校验通过立即失效，验证码不可复用
        smsCodes.remove(key);
    }

    /** 发送注册短信验证码（免登录）：用于校验注册手机号真实性 */
    @Override
    public String sendRegisterCode(String phone) {
        return sendSmsCode("register", "注册", phone);
    }

    /** 注册（R：密码加盐哈希存储；信誉分初始 100；账户正常） */
    @Override
    @Transactional
    public void register(RegisterDTO dto) {
        // 手机号真实性校验：须先获取并携带正确的短信验证码（防止随意注册他人手机号）
        verifySmsCode("register", dto.getPhone(), dto.getSmsCode());
        // 学号唯一校验
        Long studentCnt = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getStudentNo, dto.getStudentNo()));
        if (studentCnt > 0) {
            throw new BusinessException("该学号已注册，请直接登录");
        }
        // 手机号唯一校验
        Long phoneCnt = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone()));
        if (phoneCnt > 0) {
            throw new BusinessException("该手机号已注册");
        }

        User user = new User();
        user.setStudentNo(dto.getStudentNo());
        user.setUsername(dto.getUsername());
        user.setPhone(dto.getPhone());
        // BCrypt 加盐哈希，禁止明文存储（密码哈希长度固定 60，VARCHAR(128) 足够）
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        // 角色默认需求方（1），可注册为接单方（2）
        int role = dto.getRole() == null ? 1 : dto.getRole();
        user.setRole(role);
        user.setDormArea(dto.getDormArea());
        // 初始化信誉分 100
        user.setCreditScore(rules.getCredit().getInitScore());
        // 账户状态正常
        user.setStatus(1);
        // 发布权限与注册身份同步：需求方（1）可发布需求，接单方（2）不可发布
        user.setCanPublish(role == 1);
        userMapper.insert(user);

        // 注册成功自动创建零余额钱包（虚拟钱包模块：balance=0.00, frozen_balance=0.00）
        Wallet wallet = new Wallet();
        wallet.setUserId(user.getUserId());
        wallet.setBalance(java.math.BigDecimal.ZERO);
        wallet.setFrozenBalance(java.math.BigDecimal.ZERO);
        walletMapper.insert(wallet);
    }

    /** 登录（学号/手机号 + 密码比对，签发 JWT） */
    @Override
    public Map<String, Object> login(LoginDTO dto) {
        User user = userMapper.selectByLoginName(dto.getLoginName());
        if (user == null) {
            throw new BusinessException("账号或密码错误");
        }
        // 密码比对：明文 + BCrypt 哈希校验
        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("账号或密码错误");
        }
        // 账号禁用直接拒绝登录
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }
        // 签发 JWT 无状态令牌
        String token = jwtUtil.generateToken(user.getUserId(), user.getUsername(), user.getRole());

        // 脱敏：登录响应不下发任何密码哈希，仅回传「是否已设置支付密码」标记
        user.setPayPasswordSet(user.getPayPasswordHash() != null);
        user.setPayPasswordHash(null);
        user.setPasswordHash(null);

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", user);
        return data;
    }

    /** 获取当前登录用户（从 JWT 拦截器注入的 userId 查询） */
    @Override
    public User getCurrentUser() {
        Object uid = request.getAttribute(JwtInterceptor.ATTR_USER_ID);
        if (uid == null) {
            throw new BusinessException(401, "未登录，请先登录");
        }
        User user = userMapper.selectById((Integer) uid);
        if (user == null) {
            throw new BusinessException(401, "登录用户不存在");
        }
        return user;
    }

    /** 更新个人资料：仅宿舍区域与手机号；学号不可修改（业务约束） */
    @Override
    @Transactional
    public void updateUserInfo(UpdateUserDTO dto) {
        User user = getCurrentUser();
        // 手机号唯一校验（排除自己）
        Long phoneCnt = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getPhone, dto.getPhone())
                        .ne(User::getUserId, user.getUserId()));
        if (phoneCnt > 0) {
            throw new BusinessException("该手机号已被其他账号使用");
        }
        user.setPhone(dto.getPhone());
        user.setDormArea(dto.getDormArea());
        // 只更新传入字段，studentNo 不参与更新
        userMapper.updateById(user);
    }

    /** 修改密码（登录状态）：比对原密码哈希，更新为新密码 BCrypt 哈希 */
    @Override
    @Transactional
    public void changePassword(ChangePasswordDTO dto) {
        User user = getCurrentUser();
        // 原密码复核：明文 + BCrypt 比对，防越权修改
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException("原密码不正确");
        }
        if (passwordEncoder.matches(dto.getNewPassword(), user.getPasswordHash())) {
            throw new BusinessException("新密码不能与原密码相同");
        }
        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(user);
    }

    /**
     * 设置 / 修改支付密码（登录状态）
     * 规则：6 位数字；BCrypt 加盐存储；不得与登录密码相同；
     * 首次设置无需原支付密码，已设置后修改须校验原支付密码
     */
    @Override
    @Transactional
    public void setPayPassword(SetPayPasswordDTO dto) {
        User user = getCurrentUser();
        assertPayPwdNotLocked(user.getUserId());
        String newPayPwd = dto.getNewPayPassword();
        // 支付密码不得与登录密码相同，避免一码通用
        if (passwordEncoder.matches(newPayPwd, user.getPasswordHash())) {
            throw new BusinessException("支付密码不能与登录密码相同");
        }
        if (!StringUtils.hasText(user.getPayPasswordHash())) {
            // 首次设置：无需原支付密码
            if (StringUtils.hasText(dto.getOldPayPassword())) {
                throw new BusinessException("首次设置支付密码无需输入原支付密码");
            }
        } else {
            // 已设置：必须校验原支付密码
            if (!StringUtils.hasText(dto.getOldPayPassword())) {
                throw new BusinessException("请输入原支付密码");
            }
            if (!passwordEncoder.matches(dto.getOldPayPassword(), user.getPayPasswordHash())) {
                recordPayPwdFailure(user.getUserId());
                throw new BusinessException("原支付密码不正确");
            }
            if (passwordEncoder.matches(newPayPwd, user.getPayPasswordHash())) {
                throw new BusinessException("新支付密码不能与原支付密码相同");
            }
        }
        // 修改成功：清除失败记录
        payPwdAttempts.remove(user.getUserId());
        User update = new User();
        update.setUserId(user.getUserId());
        update.setPayPasswordHash(passwordEncoder.encode(newPayPwd));
        userMapper.updateById(update);
    }

    /**
     * 校验支付密码（充值 / 提现 / 发布订单付钱等资金操作前调用）
     * 未设置支付密码时抛出业务异常，引导用户先到个人中心设置
     */
    @Override
    public void verifyPayPassword(Integer userId, String rawPayPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!StringUtils.hasText(user.getPayPasswordHash())) {
            throw new BusinessException("尚未设置支付密码，请先到个人中心「安全设置」中设置支付密码");
        }
        assertPayPwdNotLocked(userId);
        if (!StringUtils.hasText(rawPayPassword)) {
            throw new BusinessException("请输入支付密码");
        }
        if (!passwordEncoder.matches(rawPayPassword, user.getPayPasswordHash())) {
            recordPayPwdFailure(userId);
            throw new BusinessException("支付密码不正确");
        }
        // 验证成功：清除失败记录
        payPwdAttempts.remove(userId);
    }

    /** 支付密码锁定检查：锁定期内直接拒绝，避免继续尝试 */
    private void assertPayPwdNotLocked(Integer userId) {
        PayPwdAttempt attempt = payPwdAttempts.get(userId);
        if (attempt != null && attempt.lockUntil != null && attempt.lockUntil.isAfter(LocalDateTime.now())) {
            throw new BusinessException("支付密码错误次数过多，请 " + PAY_PWD_LOCK_MINUTES + " 分钟后再试");
        }
    }

    /** 记录一次支付密码错误：连续错误达上限则锁定并清零计数（解锁后重新计数） */
    private void recordPayPwdFailure(Integer userId) {
        payPwdAttempts.compute(userId, (key, attempt) -> {
            PayPwdAttempt fresh = attempt == null ? new PayPwdAttempt() : attempt;
            fresh.count++;
            if (fresh.count >= PAY_PWD_MAX_ATTEMPTS) {
                fresh.lockUntil = LocalDateTime.now().plusMinutes(PAY_PWD_LOCK_MINUTES);
                fresh.count = 0;
            }
            return fresh;
        });
    }

    /**
     * 找回密码第一步（免登录）：校验学号 + 注册手机号是否匹配，通过后向该手机号下发短信验证码
     * 安全策略：时间窗内限次（防暴力枚举学号 + 手机号）；发送复用短信间隔与每日次数限制
     * 关键点：仅凭学号 + 手机号无法重置密码，必须再通过短信验证码证明手机号归属
     */
    @Override
    public String sendResetCode(ResetPasswordCodeDTO dto) {
        String studentNo = dto.getStudentNo().trim();
        assertResetAttemptAllowed(studentNo);
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getStudentNo, studentNo)
                .eq(User::getPhone, dto.getPhone()));
        if (user == null) {
            recordResetAttempt(studentNo);
            // 统一提示，避免泄露账号是否存在
            throw new BusinessException("学号或手机号信息不匹配，无法找回密码");
        }
        // 验证通过：清空尝试计数，向注册手机号下发验证码（证明手机号归属）
        resetAttempts.remove(studentNo);
        return sendSmsCode("reset", "找回密码", dto.getPhone());
    }

    /** 找回密码第二步（免登录）：校验下发到注册手机号的短信验证码后重置密码 */
    @Override
    @Transactional
    public void resetPassword(ResetPasswordDTO dto) {
        String studentNo = dto.getStudentNo().trim();
        // 先校验短信验证码（证明手机号归属），验证码一次性失效
        verifySmsCode("reset", dto.getPhone(), dto.getSmsCode());
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getStudentNo, studentNo)
                .eq(User::getPhone, dto.getPhone()));
        if (user == null) {
            throw new BusinessException("学号或手机号信息不匹配，无法找回密码");
        }
        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(user);
    }

    /** 校验身份验证尝试次数：同一账号在时间窗内失败次数达上限则拒绝，防止暴力枚举 */
    private void assertResetAttemptAllowed(String studentNo) {
        ResetAttempt attempt = resetAttempts.get(studentNo);
        if (attempt == null) {
            return;
        }
        LocalDateTime windowStart = LocalDateTime.now()
                .minusMinutes(rules.getPasswordReset().getAttemptWindowMinutes());
        if (attempt.windowStart.isBefore(windowStart)) {
            // 时间窗已过，重新计数
            resetAttempts.remove(studentNo);
            return;
        }
        if (attempt.count >= rules.getPasswordReset().getMaxAttempts()) {
            throw new BusinessException("身份验证尝试次数过多，请 "
                    + rules.getPasswordReset().getAttemptWindowMinutes() + " 分钟后再试");
        }
    }

    /** 记录一次验证失败：时间窗内累加，超窗重新计数 */
    private void recordResetAttempt(String studentNo) {
        LocalDateTime windowStart = LocalDateTime.now()
                .minusMinutes(rules.getPasswordReset().getAttemptWindowMinutes());
        resetAttempts.compute(studentNo, (key, attempt) -> {
            if (attempt == null || attempt.windowStart.isBefore(windowStart)) {
                ResetAttempt fresh = new ResetAttempt();
                fresh.count = 1;
                fresh.windowStart = LocalDateTime.now();
                return fresh;
            }
            attempt.count++;
            return attempt;
        });
    }

    /** 信誉分增减（R-22）：score + delta，结果不得为负、并以 maxScore 封顶；同时写流水明细 */
    @Override
    @Transactional
    public void updateCreditScore(Integer userId, int delta, String changeType, String remark, Integer relatedOrderId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        int before = user.getCreditScore();
        int maxScore = rules.getCredit().getMaxScore();
        int rows = userMapper.updateCreditScore(userId, delta, maxScore);
        if (rows == 0) {
            // SQL 已对结果做 [0, maxScore] 封顶封底，更新失败只可能是用户不存在
            throw new BusinessException("用户不存在，信誉分更新失败");
        }
        // 重新读取实际结果：达到上限时好评不再加分，实际增量可能小于 delta
        int after = userMapper.selectById(userId).getCreditScore();
        int actual = after - before;
        if (actual == 0) {
            // 已封顶（或无可加空间），无需写流水，避免出现 +0 的噪音记录
            return;
        }
        CreditLog log = new CreditLog();
        log.setUserId(userId);
        log.setChangeAmount(actual);
        log.setChangeType(changeType);
        log.setBeforeScore(before);
        log.setAfterScore(after);
        log.setRemark(remark);
        log.setRelatedOrderId(relatedOrderId);
        creditLogMapper.insert(log);
    }

    /** 分页查询当前登录用户自己的信誉分明细（按时间倒序） */
    @Override
    public IPage<CreditLog> pageCreditLogs(int pageNum, int pageSize) {
        User user = getCurrentUser();
        return creditLogMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<CreditLog>()
                        .eq(CreditLog::getUserId, user.getUserId())
                        .orderByDesc(CreditLog::getCreateTime, CreditLog::getLogId));
    }

    /** 个人中心统计：后端全量聚合，避免前端按分页上限统计导致口径失真 */
    @Override
    public UserStatsVO getUserStats() {
        User current = getCurrentUser();
        Integer uid = current.getUserId();
        UserStatsVO stats = new UserStatsVO();
        // 发布订单总数（全量）
        stats.setPublishTotal(orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getPublisherId, uid)));
        // 发布订单取消数
        stats.setPublishCancelled(orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getPublisherId, uid)
                .eq(Order::getStatus, OrderStatus.CANCELLED)));
        // 接单完成数
        stats.setTakeCompleted(orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getTakerId, uid)
                .eq(Order::getStatus, OrderStatus.COMPLETED)));
        // 累计收入：接单已完成订单报酬合计
        stats.setIncome(orderMapper.sumRewardByTaker(uid, OrderStatus.COMPLETED));
        // 累计支出：发布已完成订单报酬合计
        stats.setExpense(orderMapper.sumRewardByPublisher(uid, OrderStatus.COMPLETED));
        // 接单后取消次数：按信誉分流水「接单者取消」统计（退回待接单后订单已不在我的接单列表）
        stats.setTakeCancelled(creditLogMapper.selectCount(new LambdaQueryWrapper<CreditLog>()
                .eq(CreditLog::getUserId, uid)
                .eq(CreditLog::getChangeType, "接单者取消")));
        // 待评价数：我参与且已完成、但我尚未评价的订单（个人中心提示，引导完成评价闭环）
        stats.setPendingEvaluation(orderMapper.countPendingEvaluation(uid));
        return stats;
    }

    /** 头像上传：校验格式与大小，保存到 user.dir/uploads/avatar/，更新用户头像字段并返回相对路径 */
    @Override
    public String updateAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        int dot = original == null ? -1 : original.lastIndexOf('.');
        if (dot >= 0) {
            ext = original.substring(dot).toLowerCase(Locale.ROOT);
        }
        List<String> allowed = Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".webp");
        if (!allowed.contains(ext)) {
            throw new BusinessException("仅支持 jpg/png/gif/webp 格式的图片");
        }
        // 内容校验：读取文件头确认是真实图片，防止改扩展名的伪装文件落地
        String realType = ImageUtil.detectImageType(file);
        if (realType == null) {
            throw new BusinessException("图片内容校验失败，请上传真实的 jpg/png/gif/webp 图片");
        }
        // 以真实类型作为落盘扩展名，不信任原始文件名
        ext = realType;
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException("图片大小不能超过 5MB");
        }
        // 保存目录：项目根目录 uploads/avatar（与 WebMvcConfig 静态映射 /uploads/** 对应）
        File dir = new File(System.getProperty("user.dir"), "uploads/avatar");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException("头像目录创建失败，请检查目录权限");
        }
        // UUID 文件名防止重名与路径穿越
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        File dest = new File(dir, filename);
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            throw new BusinessException("头像保存失败，请重试");
        }
        // 更新当前用户头像字段
        User user = getCurrentUser();
        String oldAvatar = user.getAvatar();
        User update = new User();
        update.setUserId(user.getUserId());
        update.setAvatar("/uploads/avatar/" + filename);
        userMapper.updateById(update);
        // 替换成功后清理旧头像文件，避免 uploads 目录磁盘残留
        deleteOldAvatar(oldAvatar);
        return update.getAvatar();
    }

    /** 删除旧头像文件：仅处理本地 uploads/avatar 下的文件，失败不阻断主流程 */
    private void deleteOldAvatar(String oldAvatar) {
        if (!StringUtils.hasText(oldAvatar) || !oldAvatar.startsWith("/uploads/avatar/")) {
            return;
        }
        String name = oldAvatar.substring("/uploads/avatar/".length());
        // 防止路径穿越：文件名中不得包含分隔符
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            return;
        }
        File old = new File(new File(System.getProperty("user.dir"), "uploads/avatar"), name);
        if (old.exists() && !old.delete()) {
            log.warn("旧头像文件删除失败：{}", old.getAbsolutePath());
        }
    }

    /** 按 ID 查询用户 */
    @Override
    public User getById(Integer userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    /** 信誉等级（R-24） */
    @Override
    public String creditLevel(User user) {
        return CreditLevelUtil.levelOf(user.getCreditScore(), rules);
    }
}

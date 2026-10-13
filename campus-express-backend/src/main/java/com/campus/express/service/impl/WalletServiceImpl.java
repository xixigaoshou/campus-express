package com.campus.express.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.express.common.BusinessException;
import com.campus.express.common.WalletLogType;
import com.campus.express.entity.Wallet;
import com.campus.express.entity.WalletLog;
import com.campus.express.mapper.WalletLogMapper;
import com.campus.express.mapper.WalletMapper;
import com.campus.express.service.WalletService;
import com.campus.express.vo.WalletLogVO;
import com.campus.express.vo.WalletVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 钱包服务实现（虚拟钱包模块，R-25 ~ R-28）
 * freeze/unfreeze/settle 由订单服务在外部事务内调用，本服务不新建事务（共用外部事务，资金与订单状态原子一致）；
 * recharge / getWalletInfo 为独立入口，开启自身事务
 */
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletMapper walletMapper;
    private final WalletLogMapper walletLogMapper;

    /** 查询钱包信息（不存在则自动创建零余额钱包，兼容存量账号） */
    @Override
    @Transactional
    public WalletVO getWalletInfo(Integer userId) {
        Wallet wallet = getOrCreateWallet(userId);
        WalletVO vo = new WalletVO();
        vo.setUserId(wallet.getUserId());
        vo.setBalance(wallet.getBalance());
        vo.setFrozenBalance(wallet.getFrozenBalance());
        return vo;
    }

    /** 分页查询本人资金流水（按时间倒序，logType 转文本；logType 非空时按类型过滤） */
    @Override
    public IPage<WalletLogVO> pageWalletLogs(Integer userId, int pageNum, int pageSize, Integer logType) {
        LambdaQueryWrapper<WalletLog> wrapper = new LambdaQueryWrapper<WalletLog>()
                .eq(WalletLog::getUserId, userId)
                .eq(logType != null, WalletLog::getLogType, logType)
                .orderByDesc(WalletLog::getCreateTime, WalletLog::getId);
        Page<WalletLog> page = walletLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return page.convert(this::toVO);
    }

    /** 冻结报酬（R-25/R-26）：余额校验 + 扣可用增冻结 + 写冻结流水 */
    @Override
    public void freezeBalance(Integer userId, Integer orderId, BigDecimal rewardAmount) {
        freeze(userId, orderId, rewardAmount,
                orderId == null ? "发布订单冻结报酬" : "发布订单冻结报酬（订单#" + orderId + "）");
    }

    /** 解冻退回（R-27）：扣冻结余额返还可用余额 + 写解冻流水 */
    @Override
    public void unfreezeBalance(Integer userId, Integer orderId, BigDecimal rewardAmount) {
        unfreeze(userId, orderId, rewardAmount, "订单取消，冻结报酬解冻退回（订单#" + orderId + "）");
    }

    /** 调整订单冻结金额：delta>0 追加冻结；delta<0 解冻差额（修改待接单订单报酬时保证冻结额与报酬一致） */
    @Override
    public void adjustFrozenBalance(Integer userId, Integer orderId, BigDecimal delta) {
        if (delta == null || delta.signum() == 0) {
            return;
        }
        if (delta.signum() > 0) {
            freeze(userId, orderId, delta, "订单修改报酬，冻结差额补足（订单#" + orderId + "）");
        } else {
            unfreeze(userId, orderId, delta.negate(), "订单修改报酬，冻结差额解冻退回（订单#" + orderId + "）");
        }
    }

    /** 冻结公共逻辑：校验可用余额 → 原子扣可用增冻结 → 写冻结流水 */
    private void freeze(Integer userId, Integer orderId, BigDecimal amount, String remark) {
        Wallet wallet = getOrCreateWallet(userId);
        // R-25：校验可用余额 ≥ 冻结金额
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new BusinessException("钱包可用余额不足（当前 ¥" + wallet.getBalance()
                    + "，需 ¥" + amount + "），请先充值");
        }
        // 原子扣减：并发下以条件更新兜底（balance >= amount）
        if (walletMapper.freeze(userId, amount) == 0) {
            throw new BusinessException("钱包可用余额不足，冻结失败，请先充值");
        }
        WalletLog log = new WalletLog();
        log.setUserId(userId);
        log.setRelatedOrderId(orderId);
        log.setLogType(WalletLogType.ORDER_FROZEN.getCode());
        log.setAmount(amount.negate());
        log.setBeforeBalance(wallet.getBalance());
        log.setAfterBalance(wallet.getBalance().subtract(amount));
        log.setBeforeFrozen(wallet.getFrozenBalance());
        log.setAfterFrozen(wallet.getFrozenBalance().add(amount));
        log.setRemark(remark);
        walletLogMapper.insert(log);
    }

    /**
     * 解冻公共逻辑：扣冻结返还可用 → 写解冻流水
     * 幂等按「订单维度」而非「钱包维度」判断：先推算该订单当前净冻结额（Σ冻结 − Σ解冻），
     * 已为 0 说明该订单资金已全部退回，直接返回。仅按钱包总冻结额判断，会在用户还有其他
     * 在途订单时误判为「可解冻」，把别的订单的冻结额错误退还（资金泄漏）。
     */
    private void unfreeze(Integer userId, Integer orderId, BigDecimal amount, String remark) {
        BigDecimal netFrozen = orderId == null ? null : netFrozenOfOrder(userId, orderId);
        if (netFrozen != null && netFrozen.signum() <= 0) {
            return;
        }
        // 退款额以该订单净冻结额为上限，避免侵占该用户其他订单的冻结额
        BigDecimal refund = netFrozen == null ? amount : netFrozen.min(amount);

        Wallet wallet = getOrCreateWallet(userId);
        if (walletMapper.unfreeze(userId, refund) == 0) {
            // 兜底：钱包总冻结额不足（理论上不应发生），若已为 0 则视为已解冻完成，静默返回
            if (wallet.getFrozenBalance().compareTo(BigDecimal.ZERO) == 0) {
                return;
            }
            throw new BusinessException("冻结余额不足，解冻失败，请检查钱包状态");
        }
        WalletLog log = new WalletLog();
        log.setUserId(userId);
        log.setRelatedOrderId(orderId);
        log.setLogType(WalletLogType.ORDER_UNFROZEN.getCode());
        log.setAmount(refund);
        log.setBeforeBalance(wallet.getBalance());
        log.setAfterBalance(wallet.getBalance().add(refund));
        log.setBeforeFrozen(wallet.getFrozenBalance());
        log.setAfterFrozen(wallet.getFrozenBalance().subtract(refund));
        log.setRemark(remark);
        walletLogMapper.insert(log);
    }

    /**
     * 推算某订单当前净冻结额 = Σ冻结流水 - Σ解冻流水
     * 冻结流水 amount 记为负、解冻流水记为正，故净冻结额 = -Σamount
     */
    private BigDecimal netFrozenOfOrder(Integer userId, Integer orderId) {
        List<WalletLog> logs = walletLogMapper.selectList(new LambdaQueryWrapper<WalletLog>()
                .eq(WalletLog::getUserId, userId)
                .eq(WalletLog::getRelatedOrderId, orderId)
                .in(WalletLog::getLogType, List.of(WalletLogType.ORDER_FROZEN.getCode(),
                        WalletLogType.ORDER_UNFROZEN.getCode())));
        BigDecimal net = BigDecimal.ZERO;
        for (WalletLog l : logs) {
            net = net.subtract(l.getAmount());
        }
        return net;
    }

    /** 订单结算（R-28）：扣发布者冻结 + 接单者入账 + 两条流水 */
    @Override
    public void settleOrderReward(Integer publisherId, Integer takerId, Integer orderId, BigDecimal rewardAmount) {
        // 幂等保护：同一订单只允许结算一次。发布者「确认收货」与定时任务「超时自动确认」可能并发触发，
        // 若无保护会重复扣款/重复入账；已存在该订单的报酬支出流水则视为已结算，直接返回。
        long settled = walletLogMapper.selectCount(new LambdaQueryWrapper<WalletLog>()
                .eq(WalletLog::getRelatedOrderId, orderId)
                .eq(WalletLog::getLogType, WalletLogType.ORDER_PAY.getCode()));
        if (settled > 0) {
            return;
        }
        // 结算前对齐该订单冻结额：订单存续期间可能因「修改待接单订单报酬」部分解冻（adjustFrozenBalance），
        // 导致冻结余额与当前报酬不一致；此处按流水推算该订单净冻结额，不足报酬时补冻结差额，
        // 既避免结算时冻结不足失败，也避免多冻结的差额永久滞留在冻结余额中无法使用。
        BigDecimal frozenForOrder = netFrozenOfOrder(publisherId, orderId);
        BigDecimal shortfall = rewardAmount.subtract(frozenForOrder);
        if (shortfall.signum() > 0) {
            freezeBalance(publisherId, orderId, shortfall);
        }

        // ① 扣发布者冻结余额（报酬支出）
        Wallet pubWallet = getOrCreateWallet(publisherId);
        if (walletMapper.settlePayFrozen(publisherId, rewardAmount) == 0) {
            throw new BusinessException("发布者冻结余额不足，订单结算失败");
        }
        WalletLog payLog = new WalletLog();
        payLog.setUserId(publisherId);
        payLog.setRelatedOrderId(orderId);
        payLog.setLogType(WalletLogType.ORDER_PAY.getCode());
        payLog.setAmount(rewardAmount.negate());
        payLog.setBeforeBalance(pubWallet.getBalance());
        payLog.setAfterBalance(pubWallet.getBalance());
        payLog.setBeforeFrozen(pubWallet.getFrozenBalance());
        payLog.setAfterFrozen(pubWallet.getFrozenBalance().subtract(rewardAmount));
        payLog.setRemark("订单完成，报酬支出扣减（订单#" + orderId + "）");
        walletLogMapper.insert(payLog);

        // ② 增加接单者可用余额（报酬收入）
        Wallet takWallet = getOrCreateWallet(takerId);
        walletMapper.settleIncome(takerId, rewardAmount);
        WalletLog incomeLog = new WalletLog();
        incomeLog.setUserId(takerId);
        incomeLog.setRelatedOrderId(orderId);
        incomeLog.setLogType(WalletLogType.ORDER_INCOME.getCode());
        incomeLog.setAmount(rewardAmount);
        incomeLog.setBeforeBalance(takWallet.getBalance());
        incomeLog.setAfterBalance(takWallet.getBalance().add(rewardAmount));
        incomeLog.setBeforeFrozen(takWallet.getFrozenBalance());
        incomeLog.setAfterFrozen(takWallet.getFrozenBalance());
        incomeLog.setRemark("订单完成，报酬结算收入（订单#" + orderId + "）");
        walletLogMapper.insert(incomeLog);
    }

    /** 模拟充值（无真实支付）：增加可用余额 + 写入充值流水 */
    @Override
    @Transactional
    public void recharge(Integer userId, BigDecimal amount) {
        Wallet wallet = getOrCreateWallet(userId);
        walletMapper.recharge(userId, amount);
        WalletLog log = new WalletLog();
        log.setUserId(userId);
        log.setRelatedOrderId(null);
        log.setLogType(WalletLogType.RECHARGE.getCode());
        log.setAmount(amount);
        log.setBeforeBalance(wallet.getBalance());
        log.setAfterBalance(wallet.getBalance().add(amount));
        log.setBeforeFrozen(wallet.getFrozenBalance());
        log.setAfterFrozen(wallet.getFrozenBalance());
        log.setRemark("模拟充值（无真实支付）");
        walletLogMapper.insert(log);
    }

    /** 提现（模拟，无真实支付通道）：校验可用余额 → 扣减可用余额 → 写提现流水 */
    @Override
    @Transactional
    public void withdraw(Integer userId, BigDecimal amount) {
        Wallet wallet = getOrCreateWallet(userId);
        // 余额校验：可用余额不足直接拒绝（与冻结/结算一致的防超扣策略）
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new BusinessException("钱包可用余额不足（当前 ¥" + wallet.getBalance()
                    + "，需 ¥" + amount + "），无法提现");
        }
        // 原子扣减：并发下以条件更新兜底（balance >= amount）
        if (walletMapper.withdraw(userId, amount) == 0) {
            throw new BusinessException("钱包可用余额不足，提现失败");
        }
        WalletLog log = new WalletLog();
        log.setUserId(userId);
        log.setRelatedOrderId(null);
        log.setLogType(WalletLogType.WITHDRAW.getCode());
        log.setAmount(amount.negate());
        log.setBeforeBalance(wallet.getBalance());
        log.setAfterBalance(wallet.getBalance().subtract(amount));
        log.setBeforeFrozen(wallet.getFrozenBalance());
        log.setAfterFrozen(wallet.getFrozenBalance());
        log.setRemark("提现（模拟）");
        walletLogMapper.insert(log);
    }

    /** 查询钱包，不存在则创建零余额钱包（注册时已自动创建；兼容功能上线前的存量账号） */
    private Wallet getOrCreateWallet(Integer userId) {
        Wallet wallet = walletMapper.selectOne(new LambdaQueryWrapper<Wallet>()
                .eq(Wallet::getUserId, userId));
        if (wallet == null) {
            wallet = new Wallet();
            wallet.setUserId(userId);
            wallet.setBalance(BigDecimal.ZERO);
            wallet.setFrozenBalance(BigDecimal.ZERO);
            walletMapper.insert(wallet);
        }
        return wallet;
    }

    /** 实体转 VO：logType code → 文本 */
    private WalletLogVO toVO(WalletLog log) {
        WalletLogVO vo = new WalletLogVO();
        WalletLogType type = WalletLogType.of(log.getLogType());
        vo.setLogTypeText(type == null ? "未知类型" : type.getText());
        vo.setAmount(log.getAmount());
        vo.setBeforeBalance(log.getBeforeBalance());
        vo.setAfterBalance(log.getAfterBalance());
        vo.setBeforeFrozen(log.getBeforeFrozen());
        vo.setAfterFrozen(log.getAfterFrozen());
        vo.setRelatedOrderId(log.getRelatedOrderId());
        vo.setRemark(log.getRemark());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }
}

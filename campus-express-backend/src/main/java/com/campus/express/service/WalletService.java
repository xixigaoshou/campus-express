package com.campus.express.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.express.vo.WalletLogVO;
import com.campus.express.vo.WalletVO;

import java.math.BigDecimal;

/**
 * 钱包服务（虚拟钱包模块，规则 R-25 ~ R-28）
 * 资金操作（冻结/解冻/结算）由订单服务在外部事务内调用，本服务不新建事务，
 * 保证资金变更与订单状态变更原子一致；recharge 为独立入口，自带事务
 */
public interface WalletService {

    /**
     * 查询钱包信息（不存在则自动创建零余额钱包，兼容存量账号）
     *
     * @param userId 用户 ID
     * @return 钱包信息（可用余额 / 冻结余额）
     */
    WalletVO getWalletInfo(Integer userId);

    /**
     * 分页查询资金流水（仅本人，按时间倒序，logType 转文本）
     *
     * @param userId   用户 ID
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页条数
     * @param logType  流水类型过滤（WalletLogType code，可空=全部）
     * @return 资金流水分页
     */
    IPage<WalletLogVO> pageWalletLogs(Integer userId, int pageNum, int pageSize, Integer logType);

    /**
     * 冻结报酬（R-25/R-26）：校验余额 ≥ 报酬，扣可用余额增冻结余额，写冻结流水
     * 余额不足抛 BusinessException，阻止订单创建
     *
     * @param userId       发布者用户 ID
     * @param orderId      关联订单 ID（发布阶段订单未生成，传 null）
     * @param rewardAmount 冻结金额（订单报酬）
     */
    void freezeBalance(Integer userId, Integer orderId, BigDecimal rewardAmount);

    /**
     * 解冻退回（R-27）：扣冻结余额返还可用余额，写解冻流水
     *
     * @param userId       发布者用户 ID
     * @param orderId      关联订单 ID
     * @param rewardAmount 解冻金额（订单报酬）
     */
    void unfreezeBalance(Integer userId, Integer orderId, BigDecimal rewardAmount);

    /**
     * 调整订单冻结金额（修改待接单订单报酬时调用）：delta>0 追加冻结，delta<0 解冻差额
     * 保证冻结余额始终等于订单当前报酬，避免提高报酬后结算冻结不足、降低报酬后多冻结不退回
     *
     * @param userId  发布者用户 ID
     * @param orderId 关联订单 ID
     * @param delta   报酬变化量（新报酬 - 原报酬）
     */
    void adjustFrozenBalance(Integer userId, Integer orderId, BigDecimal delta);

    /**
     * 订单结算（R-28）：扣发布者冻结余额，增加接单者可用余额，生成两条流水
     *
     * @param publisherId  发布者用户 ID
     * @param takerId      接单者用户 ID
     * @param orderId      关联订单 ID
     * @param rewardAmount 结算金额（订单报酬）
     */
    void settleOrderReward(Integer publisherId, Integer takerId, Integer orderId, BigDecimal rewardAmount);

    /**
     * 模拟充值（无真实支付）：增加可用余额，写入充值流水
     *
     * @param userId 用户 ID
     * @param amount 充值金额（0.5 ~ 100 元，Controller 层已校验）
     */
    void recharge(Integer userId, BigDecimal amount);

    /**
     * 提现（模拟，无真实支付通道）：扣减可用余额，写入提现流水
     * 可用余额不足抛 BusinessException，避免提成负数
     *
     * @param userId 用户 ID
     * @param amount 提现金额（0.5 ~ 100 元，Controller 层已校验）
     */
    void withdraw(Integer userId, BigDecimal amount);
}

package com.campus.express.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.express.entity.Wallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 钱包表 Mapper（t_wallet）
 * 基础增删改查由 BaseMapper 提供；自定义余额冻结/解冻/结算/充值等原子更新（条件更新防超扣）
 */
@Mapper
public interface WalletMapper extends BaseMapper<Wallet> {

    /**
     * 冻结报酬（R-26）：可用余额 - amount，冻结余额 + amount
     * 条件：balance >= amount，余额不足时影响行数为 0，避免扣成负数
     *
     * @param userId 用户 ID
     * @param amount 冻结金额（报酬）
     * @return 更新行数：1 成功，0 余额不足
     */
    int freeze(@Param("userId") Integer userId, @Param("amount") BigDecimal amount);

    /**
     * 解冻退回（R-27）：冻结余额 - amount，可用余额 + amount
     * 条件：frozen_balance >= amount，防止超额解冻
     *
     * @param userId 用户 ID
     * @param amount 解冻金额（报酬）
     * @return 更新行数：1 成功，0 冻结余额不足
     */
    int unfreeze(@Param("userId") Integer userId, @Param("amount") BigDecimal amount);

    /**
     * 结算扣减发布者冻结（R-28 前半）：冻结余额 - amount
     * 条件：frozen_balance >= amount
     *
     * @param userId 发布者用户 ID
     * @param amount 结算报酬金额
     * @return 更新行数：1 成功，0 冻结余额不足
     */
    int settlePayFrozen(@Param("userId") Integer userId, @Param("amount") BigDecimal amount);

    /**
     * 结算入账接单者（R-28 后半）：可用余额 + amount
     *
     * @param userId 接单者用户 ID
     * @param amount 结算报酬金额
     * @return 更新行数
     */
    int settleIncome(@Param("userId") Integer userId, @Param("amount") BigDecimal amount);

    /**
     * 模拟充值：可用余额 + amount（无真实支付）
     *
     * @param userId 用户 ID
     * @param amount 充值金额
     * @return 更新行数
     */
    int recharge(@Param("userId") Integer userId, @Param("amount") BigDecimal amount);

    /**
     * 提现（模拟）：可用余额 - amount
     * 条件：balance >= amount，余额不足时影响行数为 0，避免提成负数
     *
     * @param userId 用户 ID
     * @param amount 提现金额
     * @return 更新行数：1 成功，0 余额不足
     */
    int withdraw(@Param("userId") Integer userId, @Param("amount") BigDecimal amount);
}

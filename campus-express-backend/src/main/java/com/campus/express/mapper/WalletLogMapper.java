package com.campus.express.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.express.entity.WalletLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 钱包资金流水表 Mapper（t_wallet_log）
 * 分页查询在 Service 层用 LambdaQueryWrapper 完成（本人流水、时间倒序），无需自定义 SQL
 */
@Mapper
public interface WalletLogMapper extends BaseMapper<WalletLog> {
}

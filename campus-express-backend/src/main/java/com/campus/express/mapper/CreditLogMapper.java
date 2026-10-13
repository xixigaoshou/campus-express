package com.campus.express.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.express.entity.CreditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 信誉分明细流水 Mapper（t_credit_log）
 * 基础增删改查 + 分页由 BaseMapper 提供
 */
@Mapper
public interface CreditLogMapper extends BaseMapper<CreditLog> {
}

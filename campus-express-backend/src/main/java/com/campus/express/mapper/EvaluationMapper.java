package com.campus.express.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.express.entity.Evaluation;
import com.campus.express.vo.EvaluationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 评价记录表 Mapper（t_evaluation）
 * 订单完成后双方互评（规则 R-21），信誉分计算的数据来源
 */
@Mapper
public interface EvaluationMapper extends BaseMapper<Evaluation> {

    /**
     * 订单评价列表（关联双方昵称），订单详情页展示
     */
    List<EvaluationVO> selectByOrderId(@Param("orderId") Integer orderId);

    /**
     * 查某订单中某评价者的评价：判断该用户是否已评价（避免重复评价，R-21）
     */
    Evaluation selectByOrderAndEvaluator(@Param("orderId") Integer orderId,
                                         @Param("evaluatorId") Integer evaluatorId);

    /**
     * 某用户收到的评价分页（关联双方昵称 + 订单摘要，评价记录列表页展示）
     */
    IPage<EvaluationVO> selectPageByEvaluatee(Page<EvaluationVO> page,
                                              @Param("evaluateeId") Integer evaluateeId);
}

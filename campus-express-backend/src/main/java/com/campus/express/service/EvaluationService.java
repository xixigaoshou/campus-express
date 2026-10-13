package com.campus.express.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.express.dto.EvaluationCreateDTO;
import com.campus.express.vo.EvaluationVO;

import java.util.List;

/**
 * 评价服务（3.9 评价与信誉管理，规则 R-21 ~ R-22）
 * 订单完成后双方互评，评价结果动态影响被评价方信誉分
 */
public interface EvaluationService {

    /**
     * 创建评价（R-21）：订单已完成 + 完成 7 天内 + 同订单同用户不重复评价
     * 保存评价后按星级自动变更被评价方信誉分（R-22）
     */
    void createEvaluation(EvaluationCreateDTO dto);

    /**
     * 获取订单全部双向评价（EvaluationVO 带评价人/被评价人昵称）
     */
    List<EvaluationVO> getOrderEvaluationList(Integer orderId);

    /**
     * 评价记录分页：仅查询当前登录用户自己收到的评价（带双方昵称 + 订单摘要）
     * 强制以当前登录用户为被评价者，避免传入他人 ID 越权查看他人评价（含订单取件码等摘要）
     */
    IPage<EvaluationVO> getEvaluationPage(long current, long size);
}

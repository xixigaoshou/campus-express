package com.campus.express.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.express.common.Result;
import com.campus.express.dto.EvaluationCreateDTO;
import com.campus.express.service.EvaluationService;
import com.campus.express.vo.EvaluationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 评价接口（/api/evaluation）
 * 订单完成后双方互评；全部需登录
 */
@RestController
@RequestMapping("/api/evaluation")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;

    /**
     * 创建评价
     * 权限：已登录，且为订单双方之一；约束：订单已完成（R-21）、完成 7 天内有效、
     * 同一用户对同一订单不可重复评价；星级自动联动被评价方信誉分（R-22）
     */
    @PostMapping("/create")
    public Result<Void> create(@RequestBody @Valid EvaluationCreateDTO dto) {
        evaluationService.createEvaluation(dto);
        return Result.success("评价成功", null);
    }

    /**
     * 获取订单双向评价列表（EvaluationVO 带评价人/被评价人昵称）
     * 权限：已登录，且仅订单发布者或接单者可查看（非参与者返回 403，防 orderId 遍历越权）
     * @param orderId 订单 ID
     */
    @GetMapping("/list/{orderId}")
    public Result<List<EvaluationVO>> list(@PathVariable Integer orderId) {
        return Result.success(evaluationService.getOrderEvaluationList(orderId));
    }

    /**
     * 评价记录分页：查询当前登录用户自己收到的评价（带双方昵称 + 关联订单摘要）
     * 权限：已登录；强制以当前登录用户为被评价者，不接收被评价者 ID 参数，避免越权查看他人评价
     * @param pageNum 页码，从 1 开始
     * @param pageSize 每页条数
     */
    @GetMapping("/page")
    public Result<IPage<EvaluationVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(evaluationService.getEvaluationPage(pageNum, pageSize));
    }
}

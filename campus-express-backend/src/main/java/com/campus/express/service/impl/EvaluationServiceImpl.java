package com.campus.express.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.express.common.BusinessException;
import com.campus.express.common.OrderStatus;
import com.campus.express.config.BusinessRulesConfig;
import com.campus.express.dto.EvaluationCreateDTO;
import com.campus.express.entity.Evaluation;
import com.campus.express.entity.Order;
import com.campus.express.entity.User;
import com.campus.express.mapper.EvaluationMapper;
import com.campus.express.mapper.OrderMapper;
import com.campus.express.service.EvaluationService;
import com.campus.express.service.NotificationService;
import com.campus.express.service.UserService;
import com.campus.express.vo.EvaluationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价服务实现（3.9 评价与信誉管理）
 * 规则 R-21：已完成订单 + 完成 7 天有效期内 + 同一订单同一用户不重复评价（双方互评）
 * 规则 R-22：4-5 星好评被评价方 +1 分；1-3 星差评 -2 分，信誉分动态计算
 */
@Service
@RequiredArgsConstructor
public class EvaluationServiceImpl implements EvaluationService {

    private final EvaluationMapper evaluationMapper;
    private final OrderMapper orderMapper;
    private final UserService userService;
    private final NotificationService notificationService;
    private final BusinessRulesConfig rules;

    /**
     * 创建评价（R-21 / R-22）：
     * ① 订单状态必须为已完成
     * ② 评价时间在订单完成 7 天有效期内（完成时间取 update_time 自动记录值）
     * ③ 同一用户对同一订单不重复评价
     * ④ 保存评价记录
     * ⑤ 按星级自动变更被评价方信誉分（R-22）
     */
    @Override
    @Transactional
    public void createEvaluation(EvaluationCreateDTO dto) {
        User evaluator = userService.getCurrentUser();
        Order order = orderMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 权限：仅订单发布者或接单者可评价（越权拦截，与留言参与者校验一致）
        boolean participant = order.getPublisherId().equals(evaluator.getUserId())
                || (order.getTakerId() != null && order.getTakerId().equals(evaluator.getUserId()));
        if (!participant) {
            throw new BusinessException(403, "仅订单发布者或接单者可评价");
        }

        // R-21 ①：仅已完成订单可评价
        if (!OrderStatus.COMPLETED.equals(order.getStatus())) {
            throw new BusinessException("订单未完成，暂不能评价");
        }

        // R-21 ②：评价有效期 —— 订单完成（update_time 记录）后 validDays 天内
        LocalDateTime deadline = order.getUpdateTime().plusDays(rules.getEvaluation().getValidDays());
        if (LocalDateTime.now().isAfter(deadline)) {
            throw new BusinessException("订单已完成超过 " + rules.getEvaluation().getValidDays()
                    + " 天，评价已过期");
        }

        // R-21 ③：同一用户对同一订单只能评价一次（双方互评）
        Evaluation exist = evaluationMapper.selectByOrderAndEvaluator(dto.getOrderId(), evaluator.getUserId());
        if (exist != null) {
            throw new BusinessException("您已评价过该订单，不能重复评价");
        }

        // 确定被评价方：发布者评价接单者，接单者评价发布者
        Integer evaluateeId = order.getPublisherId().equals(evaluator.getUserId())
                ? order.getTakerId() : order.getPublisherId();
        if (evaluateeId == null) {
            throw new BusinessException("订单无接单者，无法评价");
        }

        // ④ 保存评价记录
        Evaluation evaluation = new Evaluation();
        evaluation.setOrderId(order.getOrderId());
        evaluation.setEvaluatorId(evaluator.getUserId());
        evaluation.setEvaluateeId(evaluateeId);
        evaluation.setStarLevel(dto.getStarLevel());
        evaluation.setContent(dto.getContent());
        evaluationMapper.insert(evaluation);

        // ⑤ 信誉分变更（R-22）：星级 >= goodStar(4) 好评 +goodAdd(1) 分；<= badStar(3) 差评 -badDeduct(2) 分
        int delta = dto.getStarLevel() >= rules.getCredit().getGoodStar()
                ? rules.getCredit().getGoodAdd()
                : -rules.getCredit().getBadDeduct();
        boolean good = delta > 0;
        userService.updateCreditScore(evaluateeId, delta,
                good ? "评价好评" : "评价差评",
                (good ? "收到好评（订单#" : "收到差评（订单#") + dto.getOrderId() + "）",
                dto.getOrderId());

        // ⑥ 评价闭环：站内通知被评价方（WebSocket 实时推送刷新角标）
        notificationService.send(evaluateeId, dto.getOrderId(),
                "您收到一条" + (good ? "好评" : "差评") + "（订单#" + dto.getOrderId()
                        + "，" + dto.getStarLevel() + " 星），信誉分" + (good ? "+" : "") + delta);
    }

    /**
     * 获取订单全部双向评价，EvaluationVO 带双方昵称（用户关联查询技术点）
     * 权限：仅订单发布者或接单者可查看——评价含双方昵称与评价内容，
     * 不加参与者校验时任意登录用户可凭 orderId 遍历读取他人评价（IDOR 越权）
     */
    @Override
    public List<EvaluationVO> getOrderEvaluationList(Integer orderId) {
        User viewer = userService.getCurrentUser();
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // 与 createEvaluation 使用同一套参与者判定
        boolean participant = order.getPublisherId().equals(viewer.getUserId())
                || (order.getTakerId() != null && order.getTakerId().equals(viewer.getUserId()));
        if (!participant) {
            throw new BusinessException(403, "仅订单发布者或接单者可查看该订单的评价");
        }
        return evaluationMapper.selectByOrderId(orderId);
    }

    /** 评价记录分页：强制查询当前登录用户自己收到的评价，杜绝越权查看他人评价 */
    @Override
    public IPage<EvaluationVO> getEvaluationPage(long current, long size) {
        Integer selfId = userService.getCurrentUser().getUserId();
        return evaluationMapper.selectPageByEvaluatee(new Page<>(current, size), selfId);
    }
}

package com.campus.express.service.impl;

import com.campus.express.common.BusinessException;
import com.campus.express.common.OrderStatus;
import com.campus.express.config.BusinessRulesConfig;
import com.campus.express.dto.EvaluationCreateDTO;
import com.campus.express.entity.Evaluation;
import com.campus.express.entity.Order;
import com.campus.express.entity.User;
import com.campus.express.mapper.EvaluationMapper;
import com.campus.express.mapper.OrderMapper;
import com.campus.express.service.NotificationService;
import com.campus.express.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评价服务单元测试：唯一键兜底（并发重复评价不得加分）
 */
@ExtendWith(MockitoExtension.class)
class EvaluationServiceImplTest {

    @Mock
    private EvaluationMapper evaluationMapper;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private UserService userService;
    @Mock
    private NotificationService notificationService;

    private EvaluationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EvaluationServiceImpl(evaluationMapper, orderMapper, userService,
                notificationService, new BusinessRulesConfig());
    }

    private EvaluationCreateDTO dto() {
        EvaluationCreateDTO dto = new EvaluationCreateDTO();
        dto.setOrderId(10);
        dto.setStarLevel(5);
        dto.setContent("很好");
        return dto;
    }

    private Order completedOrder() {
        Order o = new Order();
        o.setOrderId(10);
        o.setPublisherId(1);
        o.setTakerId(2);
        o.setStatus(OrderStatus.COMPLETED);
        o.setUpdateTime(LocalDateTime.now().minusHours(1));
        return o;
    }

    @Test
    @DisplayName("并发重复评价：唯一键冲突 → 按「已评价」拒绝，且不再变更信誉分")
    void duplicateEvaluationRejectedByUniqueKey() {
        User evaluator = new User();
        evaluator.setUserId(1);
        evaluator.setCreditScore(100);
        when(userService.getCurrentUser()).thenReturn(evaluator);
        when(orderMapper.selectById(10)).thenReturn(completedOrder());
        when(evaluationMapper.selectByOrderAndEvaluator(10, 1)).thenReturn(null);
        when(evaluationMapper.insert(any(Evaluation.class))).thenThrow(new DuplicateKeyException("uk_order_evaluator"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createEvaluation(dto()));

        assertTrue(ex.getMessage().contains("已评价"));
        verify(userService, never()).updateCreditScore(any(), anyInt(), anyString(), anyString(), any());
        verify(notificationService, never()).send(any(), any(), anyString());
    }
}

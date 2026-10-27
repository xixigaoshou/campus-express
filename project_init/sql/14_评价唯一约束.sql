-- ============================================================
-- 校园快递代取平台 - t_evaluation 增加「订单 + 评价者」唯一约束
-- 背景：Service 层「是否已评价」是查询后插入，并发下两个请求都能通过检查，
--       造成同一订单同一人两条评价、信誉分被加两次（可刷分）。
-- 幂等：先清理历史重复数据，再按需添加唯一索引，可重复执行。
-- 注意：历史重复评价已经产生的多余信誉分不会自动回滚，如需修正请人工核对 t_credit_log。
-- ============================================================

USE campus_express;

-- 同一订单同一评价者只保留最早一条（evaluation_id 最小）
DELETE e1
FROM t_evaluation e1
JOIN t_evaluation e2
  ON e1.order_id = e2.order_id
 AND e1.evaluator_id = e2.evaluator_id
 AND e1.evaluation_id > e2.evaluation_id;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_evaluation'
    AND INDEX_NAME = 'uk_order_evaluator'
);

SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE t_evaluation ADD UNIQUE KEY uk_order_evaluator (order_id, evaluator_id)',
  'DO 0');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

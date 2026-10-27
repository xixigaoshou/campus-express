-- ============================================================
-- t_evaluation 增加 (order_id, evaluator_id) 唯一约束
-- 防止并发重复评价、重复加分；幂等，可重复执行
-- 注意：历史重复评价已产生的多余信誉分不会自动回滚
-- ============================================================

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

-- ============================================================
-- t_order 增加 pickup_time（取件时间）字段
-- 用于「取件后超时未送达」的计时基准；幂等，可重复执行
-- ============================================================

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_order'
    AND COLUMN_NAME = 'pickup_time'
);

SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE t_order ADD COLUMN pickup_time DATETIME DEFAULT NULL COMMENT ''接单者确认取件时间（取件后送达超时的计时起点）'' AFTER confirm_arrival_time',
  'DO 0');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE t_order o
JOIN (
  SELECT order_id, MIN(change_time) AS pickup_at
  FROM t_order_status_history
  WHERE to_status = '取件中'
  GROUP BY order_id
) h ON h.order_id = o.order_id
SET o.pickup_time = h.pickup_at
WHERE o.pickup_time IS NULL
  AND o.status IN ('取件中', '配送中', '待收货', '已完成', '已取消');

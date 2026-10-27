-- ============================================================
-- 校园快递代取平台 - t_order 增加「取件时间」字段（增量脚本）
-- 背景：原「取件后超时未送达」用 expect_time（期望取件时间）起算，
--       用户晚于期望时间取件时会被误判超时；改为记录实际取件时间。
-- 幂等：脚本可重复执行（列已存在时跳过 ALTER）
-- ============================================================

USE campus_express;

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

-- 回填历史数据：用状态历史中最早一条「取件中」的变更时间近似实际取件时间
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

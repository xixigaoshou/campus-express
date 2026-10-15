-- ============================================================
-- 12 取件码并发唯一：生成列 + 唯一索引（增量脚本）
-- 背景：09 号脚本的取件码唯一性只由服务层「先 SELECT 判重、再 INSERT/UPDATE」保证，
--       并发下两个请求可能同时通过判重，写入同一「取件地点 + 取件码」的进行中订单。
-- 方案：用生成列 active_pickup_key 承载进行中订单的唯一键，终态（已完成/已取消）置 NULL；
--       MySQL 唯一索引允许多个 NULL，既保证「进行中唯一」，又保留「终态取件码可复用」的语义。
-- 分工：服务层预检保留（用于友好提示、避免白冻结资金），数据库唯一约束是并发下的最终防线。
-- ============================================================

USE campus_express;

-- 前置检查：若已存在重复的进行中订单，需先人工处理，否则下方 ALTER 会失败
SELECT pickup_location, pickup_code, COUNT(*) AS duplicate_count
FROM t_order
WHERE status NOT IN ('已完成', '已取消')
GROUP BY pickup_location, pickup_code
HAVING duplicate_count > 1;

ALTER TABLE t_order
    ADD COLUMN active_pickup_key VARCHAR(140)
        GENERATED ALWAYS AS (
            IF(status IN ('已完成', '已取消'), NULL,
               CONCAT(pickup_location, '|', pickup_code))
        ) VIRTUAL COMMENT '进行中订单「取件地点|取件码」唯一键，终态置 NULL 释放取件码';

ALTER TABLE t_order
    ADD UNIQUE KEY uk_active_pickup (active_pickup_key);

-- 验证：进行中订单的生成列应有值（唯一），终态订单应为 NULL（取件码已释放）
SELECT order_id, status, pickup_location, pickup_code, active_pickup_key
FROM t_order ORDER BY order_id;

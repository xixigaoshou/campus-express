-- ============================================================
-- 09 取件码字段：快递单号 express_no 改为取件码 pickup_code
-- 背景：代取场景实际以「取件码」交接包裹，用取件码比快递单号更贴合业务
-- 唯一性：同一「取件码 + 取件地点」同一时刻只能存在一个进行中订单；
--         当时仅由服务层校验，未建唯一索引（为保留已完成/已取消订单取件码可复用的语义）；
--         该并发缺口已在 12_取件码并发唯一.sql 中改用「生成列 + 唯一索引」加固，以本文件为准已过时
-- 格式：4-15 位数字或横线（如 8-2-3021、12345678）
-- 说明：本脚本仅重命名列并规范化内置演示订单（order_id 1~10）的取件码；
--       其余历史行保留原值，如格式不符可在「我的发布」中编辑修正
-- ============================================================
USE campus_express;

ALTER TABLE t_order
    CHANGE COLUMN express_no pickup_code VARCHAR(30) DEFAULT NULL COMMENT '取件码（同一取件地点内进行中订单唯一）';

-- 规范化演示订单取件码（原为快递单号，格式与新规则不符，编辑时会校验失败）
UPDATE t_order SET pickup_code = '8-2-3021' WHERE order_id = 1;
UPDATE t_order SET pickup_code = '3-5-1024' WHERE order_id = 2;
UPDATE t_order SET pickup_code = '6-1-2048' WHERE order_id = 3;
UPDATE t_order SET pickup_code = '8-3-5120' WHERE order_id = 4;
UPDATE t_order SET pickup_code = '5-2-4096' WHERE order_id = 5;
UPDATE t_order SET pickup_code = '6-4-8192' WHERE order_id = 6;
UPDATE t_order SET pickup_code = '8-1-2048' WHERE order_id = 7;
UPDATE t_order SET pickup_code = '3-1-1024' WHERE order_id = 8;
UPDATE t_order SET pickup_code = '6-2-2048' WHERE order_id = 9;
UPDATE t_order SET pickup_code = '8-4-5120' WHERE order_id = 10;
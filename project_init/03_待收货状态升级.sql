-- ============================================================
-- 校园快递代取平台 - 待收货状态升级脚本（增量执行，不丢数据）
-- 业务变更：配送完成后拆分两步
--   ① 接单者「确认送达」→ 订单进入【待收货】
--   ② 需求方「确认收货」→ 订单【已完成】（报酬结算给接单者）
--   ③ 需求方在 2 小时内未确认 → 系统自动确认收货（报酬照常结算）
-- 执行方式：在 MySQL 中执行本脚本（库已存在）
-- ============================================================
USE campus_express;

-- 1. t_order 新增字段：接单者确认送达时间（2 小时自动确认收货的计时起点）
ALTER TABLE t_order
  ADD COLUMN confirm_arrival_time DATETIME DEFAULT NULL COMMENT '接单者确认送达时间，2小时自动确认收货计时'
  AFTER update_time;

-- 2. 状态 CHECK 约束扩展：允许新增的「待收货」状态
ALTER TABLE t_order DROP CHECK chk_order_status;
ALTER TABLE t_order
  ADD CONSTRAINT chk_order_status
  CHECK (status IN ('待接单','已接单','取件中','配送中','待收货','已完成','已取消'));

-- 3. 校验：查看表结构确认字段已添加
DESC t_order;

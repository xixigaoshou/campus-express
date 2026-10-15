-- ============================================================
-- 转单接力（增量脚本）
-- 场景：接单者在取件前（已接单/取件中）无法继续履约时，可发起「转单接力」，
--       把订单挂到「接力专区」，由其他接单者接力承接。
--       接力成功后接单者换人、订单状态保持不变，原接单者不扣信誉分。
-- 设计：不新增订单状态（保持严格状态机 R-1），接力标记独立存放于 transfer_status
-- ============================================================
USE campus_express;

-- 1. t_order 增加接力标记字段（NULL=无接力）
ALTER TABLE t_order
  ADD COLUMN transfer_status VARCHAR(10) DEFAULT NULL COMMENT '转单接力状态：NULL=无 / 待接力' AFTER deliver_photo,
  ADD COLUMN transfer_reason VARCHAR(200) DEFAULT NULL COMMENT '转单原因（接力中展示）' AFTER transfer_status;

-- 2. 转单接力记录表（每次发起接力一条，用于追溯与并发控制）
CREATE TABLE IF NOT EXISTS t_order_transfer (
  transfer_id   INT          NOT NULL AUTO_INCREMENT COMMENT '转单记录唯一标识，自增',
  order_id      INT          NOT NULL COMMENT '订单ID，关联t_order',
  from_taker_id INT          NOT NULL COMMENT '原接单者用户ID，关联t_user',
  to_taker_id   INT          DEFAULT NULL COMMENT '接力接单者用户ID，未接力前为空',
  reason        VARCHAR(200) DEFAULT NULL COMMENT '转单原因',
  status        VARCHAR(10)  NOT NULL DEFAULT '待接力' COMMENT '转单状态：待接力/已接力/已撤回/已过期',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
  finish_time   DATETIME     DEFAULT NULL COMMENT '结束时间（接力成功/撤回/过期）',
  PRIMARY KEY (transfer_id),
  KEY idx_order (order_id),
  KEY idx_status (status),
  CONSTRAINT fk_transfer_order FOREIGN KEY (order_id) REFERENCES t_order (order_id),
  CONSTRAINT fk_transfer_from FOREIGN KEY (from_taker_id) REFERENCES t_user (user_id),
  CONSTRAINT fk_transfer_to FOREIGN KEY (to_taker_id) REFERENCES t_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单转单接力记录表';
-- ============================================================
-- 校园快递代取平台 - 信誉分明细流水表（增量脚本）
-- 记录每次信誉分变化（评价好评/差评、恶意取消、接单超时），可追溯（R-22 扩展）
-- 执行方式：mysql -uroot -p123456 campus_express < 本文件
-- ============================================================

USE campus_express;

DROP TABLE IF EXISTS t_credit_log;

CREATE TABLE t_credit_log (
  log_id          INT           NOT NULL AUTO_INCREMENT COMMENT '流水ID，自增',
  user_id         INT           NOT NULL COMMENT '用户ID，关联t_user',
  change_amount   INT           NOT NULL COMMENT '信誉分变化量：正=增加 负=减少',
  change_type     VARCHAR(20)   NOT NULL COMMENT '变化类型：评价好评/评价差评/接单者取消/接单超时',
  before_score    INT           NOT NULL COMMENT '变化前信誉分',
  after_score     INT           NOT NULL COMMENT '变化后信誉分',
  remark          VARCHAR(200)  DEFAULT NULL COMMENT '说明（含关联订单号）',
  related_order_id INT          DEFAULT NULL COMMENT '关联订单ID，可追溯来源',
  create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (log_id),
  KEY idx_user_time (user_id, create_time),
  CONSTRAINT fk_credit_log_user FOREIGN KEY (user_id) REFERENCES t_user (user_id)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='信誉分明细流水表';

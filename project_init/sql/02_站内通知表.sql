-- ============================================================
-- 补充建表：站内通知表 t_notification（非 5 张核心表）
-- 用途：关键状态变更时向相关方发送站内提醒（规则 R-5 / R-14~R-16）
-- 在 01_项目概览与数据库设计.md 的 schema 之后执行
-- ============================================================
USE campus_express;

CREATE TABLE IF NOT EXISTS t_notification (
  notification_id INT          NOT NULL AUTO_INCREMENT COMMENT '通知唯一标识，自增',
  user_id         INT          NOT NULL COMMENT '接收通知的用户ID，关联t_user',
  order_id        INT          DEFAULT NULL COMMENT '关联订单ID，可空',
  content         VARCHAR(500) NOT NULL COMMENT '通知内容',
  is_read         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已读：0未读 1已读',
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (notification_id),
  KEY idx_user (user_id),
  KEY idx_user_read (user_id, is_read),
  CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES t_user (user_id),
  CONSTRAINT fk_notification_order FOREIGN KEY (order_id) REFERENCES t_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='站内通知表';

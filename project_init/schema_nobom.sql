-- ============================================================
-- 校园快递代取平台 - 核心库建表脚本
-- MySQL 8.0+ / utf8mb4 / InnoDB
-- 表：t_user / t_order / t_order_status_history / t_message / t_evaluation
-- 严格遵循《需求分析报告》第 5.2 节字段设计
-- ============================================================

CREATE DATABASE IF NOT EXISTS campus_express
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE campus_express;

-- ------------------------------------------------------------
-- 1. 用户表 t_user
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_order_transfer;
DROP TABLE IF EXISTS t_evaluation;
DROP TABLE IF EXISTS t_message;
DROP TABLE IF EXISTS t_order_status_history;
DROP TABLE IF EXISTS t_order;
DROP TABLE IF EXISTS t_user;

CREATE TABLE t_user (
  user_id       INT          NOT NULL AUTO_INCREMENT COMMENT '用户唯一标识，自增',
  student_no    VARCHAR(20)  NOT NULL COMMENT '学号/工号，唯一',
  username      VARCHAR(50)  NOT NULL COMMENT '用户姓名',
  phone         VARCHAR(20)  NOT NULL COMMENT '手机号，唯一',
  password_hash VARCHAR(128) NOT NULL COMMENT '加密后的密码（BCrypt加盐哈希）',
  role          TINYINT      NOT NULL DEFAULT 1 COMMENT '角色：1需求方 2接单方',
  dorm_area     VARCHAR(50)  DEFAULT NULL COMMENT '宿舍区域，用于送达地点匹配',
  credit_score  INT          NOT NULL DEFAULT 100 COMMENT '信誉分值，默认100',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '账户状态：1正常 0禁用',
  can_publish   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '发布权限：1可发布(发布者) 0不可发布(接单者)',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_student_no (student_no),
  UNIQUE KEY uk_phone (phone),
  KEY idx_role_credit (role, credit_score)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';

-- ------------------------------------------------------------
-- 2. 代取需求订单表 t_order（主业务表，含乐观锁 version）
-- ------------------------------------------------------------
CREATE TABLE t_order (
  order_id        INT           NOT NULL AUTO_INCREMENT COMMENT '订单唯一标识，自增',
  publisher_id    INT           NOT NULL COMMENT '发布者用户ID，关联t_user',
  taker_id        INT           DEFAULT NULL COMMENT '接单者用户ID，未接单前为空',
  pickup_code     VARCHAR(30)   DEFAULT NULL COMMENT '取件码（同一取件地点内进行中订单唯一）',
  express_company VARCHAR(30)   DEFAULT NULL COMMENT '快递公司',
  pickup_location VARCHAR(100)  NOT NULL COMMENT '取件地点',
  deliver_location VARCHAR(100) NOT NULL COMMENT '送达地点',
  expect_time     DATETIME      NOT NULL COMMENT '期望取件时间',
  remark          VARCHAR(500)  DEFAULT NULL COMMENT '包裹备注',
  reward_amount   DECIMAL(6,2)  NOT NULL DEFAULT 0.00 COMMENT '报酬金额（0.5~50）',
  version         INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，初始为0',
  status          VARCHAR(10)   NOT NULL DEFAULT '待接单' COMMENT '订单状态：待接单/已接单/取件中/配送中/待收货/已完成/已取消',
  create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近更新时间',
  confirm_arrival_time DATETIME DEFAULT NULL COMMENT '接单者确认送达时间，2小时自动确认收货计时',
  cancel_reason       VARCHAR(100) DEFAULT NULL COMMENT '取消原因（发布者/接单者/超时自动取消），重新接单后清空',
  pickup_photo        VARCHAR(255) DEFAULT NULL COMMENT '取件照片相对路径（选填凭证）',
  deliver_photo       VARCHAR(255) DEFAULT NULL COMMENT '送达照片相对路径（必填凭证，无法拍照可跳过）',
  transfer_status     VARCHAR(10)  DEFAULT NULL COMMENT '转单接力状态：NULL=无 / 待接力',
  transfer_reason     VARCHAR(200) DEFAULT NULL COMMENT '转单原因（接力中展示）',
  PRIMARY KEY (order_id),
  KEY idx_publisher (publisher_id),
  KEY idx_taker (taker_id),
  KEY idx_status_time (status, create_time),
  CONSTRAINT fk_order_publisher FOREIGN KEY (publisher_id) REFERENCES t_user (user_id),
  CONSTRAINT fk_order_taker FOREIGN KEY (taker_id) REFERENCES t_user (user_id),
  CONSTRAINT chk_order_status CHECK (status IN ('待接单','已接单','取件中','配送中','待收货','已完成','已取消')),
  CONSTRAINT chk_order_reward CHECK (reward_amount >= 0)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代取需求订单表';

-- ------------------------------------------------------------
-- 3. 订单状态历史表 t_order_status_history
-- ------------------------------------------------------------
CREATE TABLE t_order_status_history (
  history_id  INT          NOT NULL AUTO_INCREMENT COMMENT '记录唯一标识，自增',
  order_id    INT          NOT NULL COMMENT '订单ID，关联t_order',
  from_status VARCHAR(10)  DEFAULT NULL COMMENT '变更前状态',
  to_status   VARCHAR(10)  NOT NULL COMMENT '变更后状态',
  operator_id INT          NOT NULL COMMENT '操作人用户ID',
  change_note VARCHAR(200) DEFAULT NULL COMMENT '变更说明',
  change_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
  PRIMARY KEY (history_id),
  KEY idx_order (order_id),
  KEY idx_time (change_time),
  CONSTRAINT fk_history_order FOREIGN KEY (order_id) REFERENCES t_order (order_id)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单状态历史表';

-- ------------------------------------------------------------
-- 3.1 订单转单接力记录表 t_order_transfer（接单者取件前无法履约时转由他人接力）
-- ------------------------------------------------------------
CREATE TABLE t_order_transfer (
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
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单转单接力记录表';

-- ------------------------------------------------------------
-- 4. 留言消息表 t_message（以订单为会话维度）
-- ------------------------------------------------------------
CREATE TABLE t_message (
  message_id  INT            NOT NULL AUTO_INCREMENT COMMENT '留言唯一标识，自增',
  order_id    INT            NOT NULL COMMENT '所属订单ID，关联t_order',
  sender_id   INT            NOT NULL COMMENT '发送者用户ID',
  receiver_id INT            NOT NULL COMMENT '接收者用户ID',
  content     VARCHAR(1000)  NOT NULL COMMENT '留言内容',
  is_read     TINYINT        NOT NULL DEFAULT 0 COMMENT '是否已读：0未读 1已读',
  create_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (message_id),
  KEY idx_order (order_id),
  KEY idx_sender (sender_id),
  KEY idx_receiver_read (receiver_id, is_read),
  CONSTRAINT fk_message_order FOREIGN KEY (order_id) REFERENCES t_order (order_id),
  CONSTRAINT fk_message_sender FOREIGN KEY (sender_id) REFERENCES t_user (user_id),
  CONSTRAINT fk_message_receiver FOREIGN KEY (receiver_id) REFERENCES t_user (user_id)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='留言消息表';

-- ------------------------------------------------------------
-- 5. 评价记录表 t_evaluation（订单完成后双方互评）
-- ------------------------------------------------------------
CREATE TABLE t_evaluation (
  evaluation_id INT           NOT NULL AUTO_INCREMENT COMMENT '评价唯一标识，自增',
  order_id      INT           NOT NULL COMMENT '订单ID，关联t_order',
  evaluator_id  INT           NOT NULL COMMENT '评价者用户ID',
  evaluatee_id  INT           NOT NULL COMMENT '被评价者用户ID',
  star_level    TINYINT       NOT NULL COMMENT '满意度星级（1-5）',
  content       VARCHAR(500)  DEFAULT NULL COMMENT '文字评价内容',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
  PRIMARY KEY (evaluation_id),
  KEY idx_order (order_id),
  KEY idx_evaluator (evaluator_id),
  KEY idx_evaluatee (evaluatee_id),
  CONSTRAINT fk_evaluation_order FOREIGN KEY (order_id) REFERENCES t_order (order_id),
  CONSTRAINT fk_evaluation_evaluator FOREIGN KEY (evaluator_id) REFERENCES t_user (user_id),
  CONSTRAINT fk_evaluation_evaluatee FOREIGN KEY (evaluatee_id) REFERENCES t_user (user_id),
  CONSTRAINT chk_star CHECK (star_level BETWEEN 1 AND 5)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='评价记录表';


-- ============================================================
-- 校园快递代取平台 - 初始化测试数据
-- 账号密码统一为：123456（BCrypt 加盐哈希，$2a$10$…）
-- ============================================================
USE campus_express;

-- ------------------------------------------------------------
-- 用户（需求方/接单方两种角色 + 覆盖各信誉等级）
-- ------------------------------------------------------------
INSERT INTO t_user (user_id, student_no, username, phone, password_hash, role, dorm_area, credit_score, status, can_publish) VALUES
-- 需求发布者（can_publish=1，隐藏需求大厅）
(1,  '2024001', '小李',   '13800000001', '$2a$10$exZXhOr/AU20LfnCtg2pGeRdDjWkU3Fz8JMhZSy7kBBEsYEbSsFOi', 1, '1号楼', 100, 1, 1),
(2,  '2024002', '小王',   '13800000002', '$2a$10$exZXhOr/AU20LfnCtg2pGeRdDjWkU3Fz8JMhZSy7kBBEsYEbSsFOi', 1, '3号楼', 100, 1, 1),
(3,  '2024003', '小陈',   '13800000003', '$2a$10$exZXhOr/AU20LfnCtg2pGeRdDjWkU3Fz8JMhZSy7kBBEsYEbSsFOi', 1, '2号楼', 100, 1, 1),
-- 接单者（代取人员，can_publish=0，隐藏发布需求）
(11, '2024011', '小张',   '13800000011', '$2a$10$exZXhOr/AU20LfnCtg2pGeRdDjWkU3Fz8JMhZSy7kBBEsYEbSsFOi', 2, '1号楼', 120, 1, 0),  -- 优秀：可同时接5单
(12, '2024012', '小刘',   '13800000012', '$2a$10$exZXhOr/AU20LfnCtg2pGeRdDjWkU3Fz8JMhZSy7kBBEsYEbSsFOi', 2, '2号楼', 105, 1, 0),  -- 良好：可同时接3单
(13, '2024013', '小赵',   '13800000013', '$2a$10$exZXhOr/AU20LfnCtg2pGeRdDjWkU3Fz8JMhZSy7kBBEsYEbSsFOi', 2, '3号楼', 95,  1, 0),  -- 普通：可同时接2单
(14, '2024014', '小孙',   '13800000014', '$2a$10$exZXhOr/AU20LfnCtg2pGeRdDjWkU3Fz8JMhZSy7kBBEsYEbSsFOi', 2, '1号楼', 75,  1, 0),  -- 低于80阈值：禁止接单
(15, '2024015', '小周',   '13800000015', '$2a$10$exZXhOr/AU20LfnCtg2pGeRdDjWkU3Fz8JMhZSy7kBBEsYEbSsFOi', 2, '3号楼', 100, 0, 0); -- 已禁用账号

-- ------------------------------------------------------------
-- 订单（覆盖全部 6 种状态 + 各种流转测试场景）
-- ------------------------------------------------------------
INSERT INTO t_order (order_id, publisher_id, taker_id, pickup_code, express_company, pickup_location, deliver_location, expect_time, remark, reward_amount, version, status, create_time, update_time) VALUES
-- 待接单（需求大厅可见）
(1, 1, NULL, '8-2-3021', '顺丰速运', '菜鸟驿站(东区)', '1号楼501', '2026-10-02 18:00:00', '书本，约2kg', 5.00, 0, '待接单', '2026-10-02 09:00:00', '2026-10-02 09:00:00'),
(2, 2, NULL, '3-5-1024', '中通快递', '快递柜(南区3号)', '3号楼302', '2026-10-02 20:00:00', '衣物', 3.00, 0, '待接单', '2026-10-02 10:30:00', '2026-10-02 10:30:00'),
(3, 3, NULL, '6-1-2048', '圆通速递', '菜鸟驿站(西区)', '2号楼215', '2026-10-03 12:00:00', '零食，轻', 2.50, 0, '待接单', '2026-10-02 11:00:00', '2026-10-02 11:00:00'),
-- 已接单（小张已接，等待取件）
(4, 1, 11, '8-3-5120', '顺丰速运', '菜鸟驿站(东区)', '1号楼502', '2026-10-02 17:00:00', '文件袋', 6.00, 1, '已接单', '2026-10-01 15:00:00', '2026-10-02 08:30:00'),
-- 取件中（小刘已取件）
(5, 2, 12, '5-2-4096', '中通快递', '快递柜(南区5号)', '3号楼305', '2026-10-01 18:00:00', '日用品', 4.00, 1, '取件中', '2026-10-01 10:00:00', '2026-10-02 09:40:00'),
-- 配送中（小张配送中，等待需求方确认收货）
(6, 3, 11, '6-4-8192', '圆通速递', '菜鸟驿站(西区)', '2号楼208', '2026-10-01 20:00:00', '快递盒，小件', 3.50, 2, '配送中', '2026-09-30 14:00:00', '2026-10-02 10:10:00'),
-- 已完成（双方可评价，验证互评/信誉）
(7, 1, 12, '8-1-2048', '顺丰速运', '菜鸟驿站(东区)', '1号楼503', '2026-09-28 18:00:00', '电子产品', 8.00, 3, '已完成', '2026-09-28 09:00:00', '2026-09-28 19:00:00'),
(8, 2, 11, '3-1-1024', '中通快递', '快递柜(南区1号)', '3号楼310', '2026-09-27 19:00:00', '书籍', 4.00, 3, '已完成', '2026-09-27 08:00:00', '2026-09-27 20:00:00'),
-- 已取消（待接单状态取消 / 接单后取消两种）
(9, 3, NULL, '6-2-2048', '圆通速递', '菜鸟驿站(西区)', '2号楼220', '2026-10-01 15:00:00', '临时有事', 3.00, 0, '已取消', '2026-10-01 09:00:00', '2026-10-01 10:00:00'),
(10, 1, 13, '8-4-5120', '顺丰速运', '菜鸟驿站(东区)', '1号楼510', '2026-09-30 16:00:00', '小赵接单后取消测试', 5.00, 2, '已取消', '2026-09-30 11:00:00', '2026-09-30 13:00:00');

-- ------------------------------------------------------------
-- 订单状态历史（与上面订单状态流转一一对应）
-- ------------------------------------------------------------
INSERT INTO t_order_status_history (order_id, from_status, to_status, operator_id, change_note, change_time) VALUES
-- 订单4：待接单 → 已接单
(4, '待接单', '已接单', 11, '小张抢单成功', '2026-10-02 08:30:00'),
-- 订单5：待接单 → 已接单 → 取件中
(5, '待接单', '已接单', 12, '小刘抢单成功', '2026-10-01 11:00:00'),
(5, '已接单', '取件中', 12, '小刘已到驿站取件', '2026-10-02 09:40:00'),
-- 订单6：待接单 → 已接单 → 取件中 → 配送中
(6, '待接单', '已接单', 11, '小张抢单成功', '2026-09-30 15:00:00'),
(6, '已接单', '取件中', 11, '小张已取件', '2026-10-01 09:30:00'),
(6, '取件中', '配送中', 11, '小张开始配送', '2026-10-02 10:10:00'),
-- 订单7：待接单 → 已接单 → 取件中 → 配送中 → 已完成
(7, '待接单', '已接单', 12, '小刘抢单成功', '2026-09-28 10:00:00'),
(7, '已接单', '取件中', 12, '小刘已取件', '2026-09-28 12:00:00'),
(7, '取件中', '配送中', 12, '小刘开始配送', '2026-09-28 15:00:00'),
(7, '配送中', '已完成', 1, '需求方小李确认收货', '2026-09-28 19:00:00'),
-- 订单8：待接单 → 已接单 → 取件中 → 配送中 → 已完成
(8, '待接单', '已接单', 11, '小张抢单成功', '2026-09-27 09:00:00'),
(8, '已接单', '取件中', 11, '小张已取件', '2026-09-27 11:00:00'),
(8, '取件中', '配送中', 11, '小张开始配送', '2026-09-27 14:00:00'),
(8, '配送中', '已完成', 2, '需求方小王确认收货', '2026-09-27 20:00:00'),
-- 订单9：待接单 → 已取消
(9, '待接单', '已取消', 3, '发布者小陈取消（待接单可无条件取消）', '2026-10-01 10:00:00'),
-- 订单10：待接单 → 已接单 → 已取消
(10, '待接单', '已接单', 13, '小赵抢单成功', '2026-09-30 12:00:00'),
(10, '已接单', '已取消', 13, '小赵无法履约申请取消（恶意取消扣5分）', '2026-09-30 13:00:00');

-- ------------------------------------------------------------
-- 留言消息（订单4/6/7 会话，覆盖已读/未读）
-- ------------------------------------------------------------
INSERT INTO t_message (order_id, sender_id, receiver_id, content, is_read, create_time) VALUES
(4, 1, 11, '同学你好，快递在菜鸟驿站东区，麻烦取件', 1, '2026-10-02 08:35:00'),
(4, 11, 1, '好的，我下课就去取', 1, '2026-10-02 08:40:00'),
(6, 11, 3, '快递已送到2号楼楼下，请下来拿', 1, '2026-10-02 10:15:00'),
(6, 3, 11, '好的，马上下来，谢谢！', 0, '2026-10-02 10:20:00'),
(7, 12, 1, '快递已送达，请确认收货', 1, '2026-09-28 18:50:00'),
(7, 1, 12, '收到，辛苦了！', 1, '2026-09-28 19:05:00');

-- ------------------------------------------------------------
-- 评价记录（已完成订单7/8，双方互评，含好评/差评）
-- ------------------------------------------------------------
INSERT INTO t_evaluation (order_id, evaluator_id, evaluatee_id, star_level, content, create_time) VALUES
(7, 1, 12, 5, '准时送到，服务很好！', '2026-09-28 19:30:00'),
(7, 12, 1, 5, '需求方很好沟通，收货及时', '2026-09-28 19:32:00'),
(8, 2, 11, 4, '整体不错，送达略慢', '2026-09-27 20:30:00'),
(8, 11, 2, 3, '沟通顺畅，但约定时间改了好几次', '2026-09-27 20:35:00');

-- ------------------------------------------------------------
-- 校验：各表数据量
-- ------------------------------------------------------------
SELECT 't_user' AS tbl, COUNT(*) AS cnt FROM t_user
UNION ALL SELECT 't_order', COUNT(*) FROM t_order
UNION ALL SELECT 't_order_status_history', COUNT(*) FROM t_order_status_history
UNION ALL SELECT 't_message', COUNT(*) FROM t_message
UNION ALL SELECT 't_evaluation', COUNT(*) FROM t_evaluation;

-- ============================================================
-- 虚拟钱包模块（增量）：t_wallet / t_wallet_log
-- 模拟充值，无真实支付；所有资金操作必须生成流水（规则 R-25 ~ R-28）
-- 约束：不改动原有 7 张表；外键引用 t_user.user_id / t_order.order_id（INT）
-- ============================================================
USE campus_express;

CREATE TABLE IF NOT EXISTS t_wallet (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  user_id INT NOT NULL UNIQUE COMMENT '关联用户ID，外键t_user.user_id',
  balance DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '可用余额',
  frozen_balance DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '冻结余额（发布订单锁定报酬，R-26）',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近更新时间',
  CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES t_user(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户钱包';

CREATE TABLE IF NOT EXISTS t_wallet_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  user_id INT NOT NULL COMMENT '操作用户ID，外键t_user.user_id',
  related_order_id INT COMMENT '关联订单id，可为null（充值场景）',
  log_type TINYINT NOT NULL COMMENT '1充值 2订单冻结 3解冻退回 4订单结算收入 5订单支出扣减',
  amount DECIMAL(10,2) NOT NULL COMMENT '变动金额，正数增加，负数扣减',
  before_balance DECIMAL(10,2) NOT NULL COMMENT '变动前可用余额',
  after_balance DECIMAL(10,2) NOT NULL COMMENT '变动后可用余额',
  before_frozen DECIMAL(10,2) NOT NULL COMMENT '变动前冻结金额',
  after_frozen DECIMAL(10,2) NOT NULL COMMENT '变动后冻结金额',
  remark VARCHAR(255) COMMENT '备注',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  CONSTRAINT fk_walletlog_user FOREIGN KEY (user_id) REFERENCES t_user(user_id) ON DELETE CASCADE,
  CONSTRAINT fk_walletlog_order FOREIGN KEY (related_order_id) REFERENCES t_order(order_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包资金流水';

-- ------------------------------------------------------------
-- 预置测试账号初始化钱包余额（幂等：已存在则不覆盖）
-- 发布者：小李/小王 各 100；小陈 5（低余额用于测试发布失败 R-25）
-- 接单者：小张/小刘 各 50；小赵 10；小孙 5；小周 0
-- 注意：功能上线前的历史订单不追溯冻结/结算资金，明细从本模块上线后开始记录
-- ------------------------------------------------------------
INSERT INTO t_wallet (user_id, balance, frozen_balance)
SELECT u.user_id, v.balance, 0.00
FROM t_user u
JOIN (
  SELECT 1 AS user_id, 100.00 AS balance UNION ALL
  SELECT 2, 100.00 UNION ALL
  SELECT 3, 5.00 UNION ALL
  SELECT 11, 50.00 UNION ALL
  SELECT 12, 50.00 UNION ALL
  SELECT 13, 10.00 UNION ALL
  SELECT 14, 5.00 UNION ALL
  SELECT 15, 0.00
) v ON u.user_id = v.user_id
WHERE NOT EXISTS (SELECT 1 FROM t_wallet w WHERE w.user_id = u.user_id);

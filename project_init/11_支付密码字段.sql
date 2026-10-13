-- ============================================================
-- 校园快递代取平台 - 支付密码字段（增量脚本）
-- t_user 增加 pay_password_hash 列：存 BCrypt 加盐哈希，空表示尚未设置
-- 用途：充值 / 提现 / 发布订单（冻结报酬=付钱）时校验支付密码
-- ============================================================

USE campus_express;

ALTER TABLE t_user
  ADD COLUMN pay_password_hash VARCHAR(128) DEFAULT NULL COMMENT '支付密码哈希（BCrypt加盐），空表示未设置' AFTER avatar;

SELECT user_id, student_no, username, avatar, pay_password_hash FROM t_user;
-- ============================================================
-- 校园快递代取平台 - 角色发布权限升级脚本（增量执行，不丢数据）
-- 业务变更：区分【发布者】与【接单者】角色权限
--   can_publish=1 → 发布者：可发布需求，不可抢单，隐藏【需求大厅】
--   can_publish=0 → 接单者：可抢单接单，不可发布，隐藏【发布需求】
-- 预置账号：小李 2024001 → 发布者(1)；小张 2024011 → 接单者(0)
-- 手动开启发布权限：UPDATE t_user SET can_publish=1 WHERE student_no='xxx';
-- ============================================================
USE campus_express;

-- 1. t_user 新增发布权限字段（默认 0=接单者，新注册用户默认无发布权限）
ALTER TABLE t_user
  ADD COLUMN can_publish TINYINT(1) NOT NULL DEFAULT 0 COMMENT '发布权限：1可发布(发布者) 0不可发布(接单者)'
  AFTER status;

-- 2. 预置账号角色：小李=发布者，小张保持接单者
UPDATE t_user SET can_publish = 1 WHERE student_no = '2024001';

-- 3. 校验
SELECT user_id, student_no, username, can_publish FROM t_user;

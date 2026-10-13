-- ============================================================
-- 校园快递代取平台 - 用户头像字段（增量脚本）
-- t_user 增加 avatar 列：存相对路径 /uploads/avatar/xxx.png，空则前端显示姓名占位
-- ============================================================

USE campus_express;

ALTER TABLE t_user
  ADD COLUMN avatar VARCHAR(255) DEFAULT NULL COMMENT '头像地址（相对路径/uploads/avatar/），空则显示姓名占位' AFTER can_publish;

SELECT user_id, student_no, username, avatar FROM t_user;

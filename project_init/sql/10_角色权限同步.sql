-- ============================================================
-- 校园快递代取平台 - 角色与发布权限一致性修复脚本（增量执行，不丢数据）
-- 背景：注册时曾统一将 can_publish 置为 0，导致注册为「需求方」的账号无法发布需求（功能死结）。
-- 修复：发布权限与注册身份对齐 —— 需求方(role=1) 可发布，接单方(role=2) 不可发布。
-- 说明：注册逻辑已在 UserServiceImpl.register 中同步修正，本脚本用于修复历史存量数据。
-- ============================================================
USE campus_express;

-- 1. 存量数据对齐：需求方 role=1 → can_publish=1；接单方 role=2 → can_publish=0
UPDATE t_user
SET can_publish = CASE WHEN role = 1 THEN 1 ELSE 0 END;

-- 2. 校验：确认无角色与发布权限不一致的账号
SELECT user_id, student_no, username, role, can_publish
FROM t_user
ORDER BY user_id;

-- 3. 一致性检查：以下查询应返回 0 行（无 role=1 却无发布权限、或无 role=2 却有发布权限的账号）
SELECT user_id, student_no, role, can_publish
FROM t_user
WHERE (role = 1 AND can_publish = 0)
   OR (role = 2 AND can_publish = 1);
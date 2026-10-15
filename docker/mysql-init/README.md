# docker/mysql-init

MySQL 容器首次启动时自动执行的建库建表脚本（按文件名顺序执行）。

| 文件 | 来源 | 作用 |
| --- | --- | --- |
| `01_schema.sql` | `project_init/sql/schema.sql` | 基础建表：`t_user` / `t_order` / `t_order_status_history` / `t_order_transfer` / `t_message` / `t_evaluation` / `t_wallet` / `t_wallet_log` + 演示种子数据 |
| `02_notification.sql` | `project_init/sql/02_站内通知表.sql` | 站内通知表 `t_notification` |
| `03_credit_log.sql` | `project_init/sql/06_信誉分明细表.sql` | 信誉分流水表 `t_credit_log` |
| `04_user_avatar.sql` | `project_init/sql/07_用户头像字段.sql` | `t_user.avatar` 头像字段 |
| `05_pay_password.sql` | `project_init/sql/11_支付密码字段.sql` | `t_user.pay_password_hash` 支付密码字段 |

说明：`project_init/sql/schema.sql` 是「当前基线表结构」，但未包含后续增量脚本新增的通知表、信誉流水表、头像与支付密码字段。
这里按依赖顺序把它们拼成一套完整的容器初始化脚本，保证 `docker compose up` 后库结构与代码一致。

> 取件码并发唯一约束（生成列 `t_order.active_pickup_key` + 唯一索引 `uk_active_pickup`）已并入 `01_schema.sql` 基线，
> 无需单独的容器初始化脚本；**已有数据库**升级请执行 `project_init/sql/12_取件码并发唯一.sql`。

本目录内容为上述脚本的副本，改动表结构时请同步更新对应源脚本。
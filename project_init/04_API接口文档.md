# 校园快递代取平台 —— 第4部分：API 接口文档

> 项目名称：校园快递代取平台（赣南师范大学科技学院 Web 开发课程实训）
> 后端：Spring Boot 3.x + MyBatis-Plus + JWT；前端：Vue3 + Element Plus
> 文档版本：v1.0 ｜ 更新日期：2026-10-02

---

## 一、通用说明

### 1.1 基础信息

| 项目 | 说明 |
| --- | --- |
| Base URL | `http://localhost:8080/api`（本地开发，端口见 `application.yml`） |
| 请求编码 | `UTF-8` |
| 请求体格式 | `application/json;charset=UTF-8` |
| 认证方式 | JWT 无状态令牌：请求头 `Authorization: Bearer <token>` |

### 1.2 统一响应结构 `Result<T>`

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

| code | 含义 |
| --- | --- |
| 200 | 成功 |
| 400 | 参数校验失败 / 业务规则不满足（抢单失败、状态非法、重复评价等） |
| 401 | 未登录 / 令牌无效或过期（拦截器直接拦截） |
| 403 | 无权限操作（敏感操作鉴权失败） |
| 500 | 服务器内部错误 |

### 1.3 登录状态与鉴权

- **免登录接口**：`POST /user/register`、`POST /user/login`（拦截器已放行）。
- 其余**全部接口**均需携带 JWT，未登录返回 `401`：
  ```json
  { "code": 401, "message": "未登录或登录已过期，请重新登录", "data": null }
  ```
- 登录成功后，将返回的 `token` 放入后续请求头：`Authorization: Bearer eyJhbGciOi...`

### 1.4 分页参数约定

- 分页接口统一接收 `pageNum`（页码，从 1 开始，默认 1）、`pageSize`（每页条数，默认 10）。
- 返回 `data` 为 MyBatis-Plus `IPage` 结构：
  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "records": [ ],
      "total": 25,
      "size": 10,
      "current": 1,
      "pages": 3
    }
  }
  ```

---

## 二、用户接口 `/api/user`

### 2.1 用户注册

- **接口地址**：`POST /api/user/register`
- **请求头**：无（免登录）
- **访问权限**：公开

**请求参数（Body JSON）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| studentNo | String | 是 | 学号/工号，4-20 位字母数字，唯一 |
| username | String | 是 | 用户姓名 |
| phone | String | 是 | 手机号，`^1[3-9]\d{9}$`，唯一 |
| password | String | 是 | 密码，6-20 位（后端 BCrypt 加盐加密存储） |
| role | Integer | 否 | 角色：1 需求方 2 接单方，默认 1 |
| dormArea | String | 否 | 宿舍区域 |

**请求示例**
```json
{
  "studentNo": "2024001",
  "username": "张三",
  "phone": "13800001111",
  "password": "123456",
  "role": 2,
  "dormArea": "一区"
}
```

**成功响应**
```json
{ "code": 200, "message": "注册成功", "data": null }
```

**失败响应（学号/手机号重复）**
```json
{ "code": 400, "message": "该学号已注册，请直接登录", "data": null }
```

---

### 2.2 用户登录

- **接口地址**：`POST /api/user/login`
- **请求头**：无（免登录）
- **访问权限**：公开

**请求参数（Body JSON）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| loginName | String | 是 | 学号或手机号（两者均唯一） |
| password | String | 是 | 登录密码 |

**请求示例**
```json
{ "loginName": "2024001", "password": "123456" }
```

**成功响应（签发 JWT 令牌）**
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "user": {
      "userId": 1,
      "studentNo": "2024001",
      "username": "张三",
      "phone": "13800001111",
      "passwordHash": null,
      "role": 2,
      "dormArea": "一区",
      "creditScore": 100,
      "status": 1,
      "createTime": "2026-09-23 10:00:00"
    }
  }
}
```

**失败响应（账号或密码错误 / 账号禁用）**
```json
{ "code": 400, "message": "账号或密码错误", "data": null }
{ "code": 400, "message": "账号已被禁用，请联系管理员", "data": null }
```

---

### 2.3 获取当前登录用户信息

- **接口地址**：`GET /api/user/info`
- **请求头**：`Authorization: Bearer <token>`
- **访问权限**：已登录

**请求参数**：无

**成功响应（含信誉分，密码哈希已脱敏）**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": 1,
    "studentNo": "2024001",
    "username": "张三",
    "phone": "13800001111",
    "passwordHash": null,
    "role": 2,
    "dormArea": "一区",
    "creditScore": 100,
    "status": 1,
    "createTime": "2026-09-23 10:00:00"
  }
}
```

**失败响应（未登录）**
```json
{ "code": 401, "message": "未登录或登录已过期，请重新登录", "data": null }
```

---

### 2.4 更新个人资料

- **接口地址**：`PUT /api/user/info`
- **请求头**：`Authorization: Bearer <token>`
- **访问权限**：已登录（仅本人）；**学号不可修改**

**请求参数（Body JSON）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| phone | String | 是 | 手机号（唯一，排除本人） |
| dormArea | String | 否 | 宿舍区域 |

**请求示例**
```json
{ "phone": "13900002222", "dormArea": "二区" }
```

**成功响应**
```json
{ "code": 200, "message": "资料更新成功", "data": null }
```

**失败响应（手机号被占用）**
```json
{ "code": 400, "message": "该手机号已被其他账号使用", "data": null }
```

---

## 三、订单接口 `/api/order`（业务核心）

> 状态机：`待接单 → 已接单 → 取件中 → 配送中 → 已完成`；终态：`已完成 / 已取消`
> 全部接口需登录。抢单/取消/状态流转的权限与业务规则校验均在 Service 层完成。

### 3.1 发布代取需求

- **接口地址**：`POST /api/order/publish`
- **请求头**：`Authorization: Bearer <token>`
- **访问权限**：已登录（任意用户）
- **业务规则**：报酬金额 0.5 ~ 50 元（R-17）；生成待接单订单，version=0，写入首条状态历史（R-4）

**请求参数（Body JSON）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| expressNo | String | 否 | 快递单号 |
| expressCompany | String | 否 | 快递公司 |
| pickupLocation | String | 是 | 取件地点 |
| deliverLocation | String | 是 | 送达地点 |
| expectTime | String | 是 | 期望取件时间（`yyyy-MM-dd HH:mm:ss`） |
| remark | String | 否 | 包裹备注 |
| rewardAmount | BigDecimal | 是 | 报酬金额（0.5 ~ 50 元） |

**请求示例**
```json
{
  "expressNo": "SF1234567890",
  "expressCompany": "顺丰",
  "pickupLocation": "菜鸟驿站",
  "deliverLocation": "学生宿舍3栋",
  "expectTime": "2026-10-03 18:00:00",
  "remark": "请轻拿轻放",
  "rewardAmount": 5.0
}
```

**成功响应（返回新订单 ID）**
```json
{ "code": 200, "message": "发布成功", "data": 12 }
```

**失败响应（报酬超范围）**
```json
{ "code": 400, "message": "报酬金额需在 0.5 ~ 50.0 元之间", "data": null }
```

---

### 3.2 修改待接单订单

- **接口地址**：`PUT /api/order/update-wait`
- **请求头**：`Authorization: Bearer <token>`
- **访问权限**：仅订单发布者本人
- **业务规则**：仅 `待接单` 状态可修改（R-11）；已接单后禁止修改核心字段

**请求参数（Body JSON）**：同 3.1，另含 `orderId` 必填

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| orderId | Integer | 是 | 订单 ID |
| 其余字段 | - | - | 同发布接口 |

**成功响应**
```json
{ "code": 200, "message": "修改成功", "data": null }
```

**失败响应（他人订单 / 已被接单）**
```json
{ "code": 403, "message": "仅发布者可修改订单", "data": null }
{ "code": 400, "message": "订单已被接单，进入业务流转，不允许修改核心信息", "data": null }
```

---

### 3.3 取消订单

- **接口地址**：`PUT /api/order/cancel?orderId={orderId}`
- **请求头**：`Authorization: Bearer <token>`
- **访问权限**：待接单 → 发布者本人；已接单/取件中 → 接单者本人
- **业务规则**（R-11 / R-12）：
  - 待接单：发布者无条件取消，置为已取消；
  - 已接单/取件中：接单者取消 → 扣信誉分 5 → 订单退回待接单；
  - 配送中及之后禁止取消。

**请求参数（Query）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| orderId | Integer | 是 | 订单 ID |

**成功响应**
```json
{ "code": 200, "message": "订单已取消", "data": null }
```

**失败响应（状态不允许取消）**
```json
{ "code": 400, "message": "订单当前状态（配送中）不允许取消", "data": null }
```

---

### 3.4 抢单接单（乐观锁）

- **接口地址**：`PUT /api/order/take-order?orderId={orderId}`
- **请求头**：`Authorization: Bearer <token>`
- **访问权限**：已登录（接单方）
- **业务规则**（R-6 ~ R-10 / R-23）：禁止接自己的单；信誉分 ≥ 80；进行中订单数不超过等级上限；乐观锁更新 `status='待接单' AND version=读出版本`，影响行数为 0 即抢单失败；成功写入状态历史并通知发布者。

**请求参数（Query）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| orderId | Integer | 是 | 订单 ID |

**成功响应**
```json
{ "code": 200, "message": "抢单成功", "data": null }
```

**失败响应（并发冲突 / 规则不满足）**
```json
{ "code": 400, "message": "手慢了，该订单已被别人抢走，抢单失败", "data": null }
{ "code": 400, "message": "不能承接自己发布的订单", "data": null }
{ "code": 400, "message": "信誉分低于 80，暂不可接单，请先提升信誉分", "data": null }
{ "code": 400, "message": "当前信誉等级（普通）同时进行中订单已达上限 2 单，无法继续接单", "data": null }
```

---

### 3.5 更新订单状态（取件中 / 配送中）

- **接口地址**：`PUT /api/order/status`
- **请求头**：`Authorization: Bearer <token>`
- **访问权限**：仅接单者本人（敏感操作鉴权）
- **业务规则**（R-1 / R-4）：严格状态机，禁止跨状态跳转；变更成功写入 `t_order_status_history` 并通知发布者。

**请求参数（Body JSON）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| orderId | Integer | 是 | 订单 ID |
| targetStatus | String | 是 | 目标状态：`取件中`、`配送中`（接单者操作） |
| changeNote | String | 否 | 变更说明 |

**请求示例**
```json
{ "orderId": 12, "targetStatus": "取件中", "changeNote": "已到驿站取件" }
```

**成功响应**
```json
{ "code": 200, "message": "状态更新成功", "data": null }
```

**失败响应（非法流转 / 无权限）**
```json
{ "code": 400, "message": "非法状态流转：待接单 → 配送中（禁止跨状态跳转）", "data": null }
{ "code": 403, "message": "订单状态更新仅限接单者操作", "data": null }
```

---

### 3.6 需求方确认收货

- **接口地址**：`PUT /api/order/confirm-finish?orderId={orderId}`
- **请求头**：`Authorization: Bearer <token>`
- **访问权限**：仅订单发布者本人（R-3）
- **业务规则**：订单须处于 `配送中`；确认后置为 `已完成`，写状态历史并通知接单者（可评价）。

**请求参数（Query）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| orderId | Integer | 是 | 订单 ID |

**成功响应**
```json
{ "code": 200, "message": "确认收货成功", "data": null }
```

**失败响应（非发布者 / 状态非法）**
```json
{ "code": 403, "message": "确认收货仅限需求发布者操作", "data": null }
{ "code": 400, "message": "非法状态流转：取件中 → 已完成（禁止跨状态跳转）", "data": null }
```

---

### 3.7 需求大厅分页

- **接口地址**：`GET /api/order/hall-page?pageNum=1&pageSize=10`
- **请求头**：`Authorization: Bearer <token>`
- **访问权限**：已登录
- **业务规则**：仅查询 `待接单` 订单，携带发布者昵称（OrderVO），按发布时间降序。

**请求参数（Query）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| pageNum | Integer | 否 | 页码，默认 1 |
| pageSize | Integer | 否 | 每页条数，默认 10 |

**成功响应**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "orderId": 12,
        "publisherId": 1,
        "takerId": null,
        "expressNo": "SF1234567890",
        "expressCompany": "顺丰",
        "pickupLocation": "菜鸟驿站",
        "deliverLocation": "学生宿舍3栋",
        "expectTime": "2026-10-03 18:00:00",
        "remark": "请轻拿轻放",
        "rewardAmount": 5.0,
        "version": 0,
        "status": "待接单",
        "createTime": "2026-10-02 17:00:00",
        "updateTime": "2026-10-02 17:00:00",
        "publisherName": "张三",
        "takerName": null
      }
    ],
    "total": 25,
    "size": 10,
    "current": 1,
    "pages": 3
  }
}
```

---

### 3.8 我的发布订单分页

- **接口地址**：`GET /api/order/my-publish-page?pageNum=1&pageSize=10&statusList=进行中`
- **请求头**：`Authorization: Bearer <token>`
- **访问权限**：已登录（本人发布）

**请求参数（Query）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| pageNum | Integer | 否 | 页码，默认 1 |
| pageSize | Integer | 否 | 每页条数，默认 10 |
| statusList | List\<String\> | 否 | 状态分组：不传=全部；`进行中`=待接单/已接单/取件中/配送中；`历史`=已完成/已取消（可传多个） |

**请求示例**
```
GET /api/order/my-publish-page?pageNum=1&pageSize=10&statusList=进行中
GET /api/order/my-publish-page?pageNum=1&pageSize=10
```

**成功响应**：同 3.7（data.records 为 OrderVO 数组）。

---

### 3.9 我的接单订单分页

- **接口地址**：`GET /api/order/my-take-page?pageNum=1&pageSize=10&statusList=进行中`
- **请求头**：`Authorization: Bearer <token>`
- **访问权限**：已登录（本人接单）
- **请求参数**：同 3.8；**成功响应**：同 3.7。

---

### 3.10 订单详情

- **接口地址**：`GET /api/order/{orderId}`
- **请求头**：`Authorization: Bearer <token>`
- **访问权限**：已登录
- **返回内容**：订单（含双方昵称）+ 完整状态流转记录 + 双向评价列表。

**成功响应**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "order": {
      "orderId": 12,
      "publisherId": 1,
      "takerId": 2,
      "expressNo": "SF1234567890",
      "expressCompany": "顺丰",
      "pickupLocation": "菜鸟驿站",
      "deliverLocation": "学生宿舍3栋",
      "expectTime": "2026-10-03 18:00:00",
      "remark": "请轻拿轻放",
      "rewardAmount": 5.0,
      "version": 4,
      "status": "配送中",
      "createTime": "2026-10-02 17:00:00",
      "updateTime": "2026-10-02 18:30:00",
      "publisherName": "张三",
      "takerName": "李四"
    },
    "statusHistory": [
      {
        "historyId": 31,
        "orderId": 12,
        "fromStatus": null,
        "toStatus": "待接单",
        "operatorId": 1,
        "changeNote": "需求发布，进入待接单",
        "changeTime": "2026-10-02 17:00:00"
      },
      {
        "historyId": 32,
        "orderId": 12,
        "fromStatus": "待接单",
        "toStatus": "已接单",
        "operatorId": 2,
        "changeNote": "接单者 李四 抢单成功",
        "changeTime": "2026-10-02 17:05:00"
      },
      {
        "historyId": 33,
        "orderId": 12,
        "fromStatus": "已接单",
        "toStatus": "取件中",
        "operatorId": 2,
        "changeNote": "已到驿站取件",
        "changeTime": "2026-10-02 17:20:00"
      },
      {
        "historyId": 34,
        "orderId": 12,
        "fromStatus": "取件中",
        "toStatus": "配送中",
        "operatorId": 2,
        "changeNote": "开始配送",
        "changeTime": "2026-10-02 18:30:00"
      }
    ],
    "evaluations": []
  }
}
```

**失败响应（订单不存在）**
```json
{ "code": 400, "message": "订单不存在", "data": null }
```

---

## 四、留言接口 `/api/message`

> 以订单为会话维度；仅订单发布者/接单者可发送与查看会话。

### 4.1 发送订单留言

- **接口地址**：`POST /api/message/send`
- **请求头**：`Authorization: Bearer <token>`
- **访问权限**：已登录，且为订单发布者或接单者
- **业务规则**：内容经内置敏感词过滤（命中替换为 `*`）；接收者为另一方；默认未读（isRead=0）。

**请求参数（Body JSON）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| orderId | Integer | 是 | 订单 ID（会话维度） |
| content | String | 是 | 留言内容（自动敏感词过滤） |

**请求示例**
```json
{ "orderId": 12, "content": "同学你好，快递已经取到了" }
```

**成功响应**
```json
{ "code": 200, "message": "发送成功", "data": null }
```

**失败响应（非参与者 / 无留言对象）**
```json
{ "code": 403, "message": "仅订单发布者或接单者可留言", "data": null }
{ "code": 400, "message": "订单尚未被接单，暂无留言对象", "data": null }
```

---

### 4.2 订单会话消息分页

- **接口地址**：`GET /api/message/order-page/{orderId}?pageNum=1&pageSize=20`
- **请求头**：`Authorization: Bearer <token>`
- **访问权限**：已登录，且为订单参与者
- **说明**：按发送时间升序返回，MessageVO 携带发送者昵称；前端轮询此接口获取最新留言。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| orderId | Integer | 是 | 订单 ID（路径参数） |
| pageNum | Integer | 否 | 页码，默认 1 |
| pageSize | Integer | 否 | 每页条数，默认 20 |

**成功响应**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "messageId": 5,
        "orderId": 12,
        "senderId": 1,
        "receiverId": 2,
        "content": "同学你好，快递已经取到了",
        "isRead": 0,
        "createTime": "2026-10-02 18:40:00",
        "senderName": "张三"
      }
    ],
    "total": 5,
    "size": 20,
    "current": 1,
    "pages": 1
  }
}
```

---

### 4.3 标记已读

- **接口地址**：`PUT /api/message/mark-read/{orderId}`
- **请求头**：`Authorization: Bearer <token>`
- **访问权限**：已登录（本人接收的消息）
- **说明**：将该订单下发给当前用户的所有未读消息置为已读。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| orderId | Integer | 是 | 订单 ID（路径参数） |

**成功响应**
```json
{ "code": 200, "message": "已全部标记为已读", "data": null }
```

---

### 4.4 未读消息总数

- **接口地址**：`GET /api/message/unread-count`
- **请求头**：`Authorization: Bearer <token>`
- **访问权限**：已登录
- **说明**：返回当前用户全部未读消息数，用于页面角标。

**成功响应**
```json
{ "code": 200, "message": "success", "data": 3 }
```

---

## 五、评价接口 `/api/evaluation`

### 5.1 创建评价

- **接口地址**：`POST /api/evaluation/create`
- **请求头**：`Authorization: Bearer <token>`
- **访问权限**：已登录，且为订单双方之一
- **业务规则**（R-21 / R-22）：
  - ① 订单状态必须为 `已完成`；
  - ② 评价时间在订单完成 7 天有效期内（基于订单 update_time）；
  - ③ 同一用户对同一订单不可重复评价；
  - ④ 星级自动联动被评价方信誉分：4-5 星 +1 分，1-3 星 -2 分。

**请求参数（Body JSON）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| orderId | Integer | 是 | 订单 ID |
| starLevel | Integer | 是 | 星级 1-5（4 星及以上好评 +1，3 星及以下差评 -2） |
| content | String | 否 | 文字评价内容 |

**请求示例**
```json
{ "orderId": 12, "starLevel": 5, "content": "取件很快，服务很好" }
```

**成功响应**
```json
{ "code": 200, "message": "评价成功", "data": null }
```

**失败响应（规则不满足）**
```json
{ "code": 400, "message": "订单未完成，暂不能评价", "data": null }
{ "code": 400, "message": "订单已完成超过 7 天，评价已过期", "data": null }
{ "code": 400, "message": "您已评价过该订单，不能重复评价", "data": null }
```

---

### 5.2 订单双向评价列表

- **接口地址**：`GET /api/evaluation/list/{orderId}`
- **请求头**：`Authorization: Bearer <token>`
- **访问权限**：已登录
- **说明**：返回该订单全部评价，EvaluationVO 携带评价人/被评价人昵称，按时间升序。

**成功响应**
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "evaluationId": 7,
      "orderId": 12,
      "evaluatorId": 1,
      "evaluateeId": 2,
      "starLevel": 5,
      "content": "取件很快，服务很好",
      "createTime": "2026-10-02 19:00:00",
      "evaluatorName": "张三",
      "evaluateeName": "李四"
    },
    {
      "evaluationId": 8,
      "orderId": 12,
      "evaluatorId": 2,
      "evaluateeId": 1,
      "starLevel": 4,
      "content": "沟通顺畅，按时收货",
      "createTime": "2026-10-02 19:10:00",
      "evaluatorName": "李四",
      "evaluateeName": "张三"
    }
  ]
}
```

---

## 六、接口总览

| 模块 | 方法 | 路径 | 权限 |
| --- | --- | --- | --- |
| 用户 | POST | `/api/user/register` | 公开 |
| 用户 | POST | `/api/user/login` | 公开 |
| 用户 | GET | `/api/user/info` | 已登录 |
| 用户 | PUT | `/api/user/info` | 已登录（本人） |
| 订单 | POST | `/api/order/publish` | 已登录 |
| 订单 | PUT | `/api/order/update-wait` | 发布者本人 |
| 订单 | PUT | `/api/order/cancel` | 发布者/接单者本人（按状态） |
| 订单 | PUT | `/api/order/take-order` | 已登录（接单方） |
| 订单 | PUT | `/api/order/status` | 接单者本人 |
| 订单 | PUT | `/api/order/confirm-finish` | 发布者本人（R-3） |
| 订单 | GET | `/api/order/hall-page` | 已登录 |
| 订单 | GET | `/api/order/my-publish-page` | 已登录（本人） |
| 订单 | GET | `/api/order/my-take-page` | 已登录（本人） |
| 订单 | GET | `/api/order/{orderId}` | 已登录 |
| 留言 | POST | `/api/message/send` | 订单参与者 |
| 留言 | GET | `/api/message/order-page/{orderId}` | 订单参与者 |
| 留言 | PUT | `/api/message/mark-read/{orderId}` | 已登录（本人） |
| 留言 | GET | `/api/message/unread-count` | 已登录 |
| 评价 | POST | `/api/evaluation/create` | 订单双方之一 |
| 评价 | GET | `/api/evaluation/list/{orderId}` | 已登录 |

> 说明：注册/登录由 JWT 拦截器放行；其余接口统一校验 `Authorization: Bearer <token>`，未登录返回 401；敏感操作（修改/取消/状态流转/确认收货/留言/评价）在 Service 层完成二次权限校验，越权返回 403。

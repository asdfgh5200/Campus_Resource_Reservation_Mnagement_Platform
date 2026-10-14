# 校园资源预约平台 —— 接口说明书

> 适用版本：Spring Boot 3.2.5 + MyBatis-Plus + MySQL 8
> 接口文档在线版：启动后访问 `http://localhost:8080/swagger-ui.html`

---

## 1. 通用约定

### 1.1 基础信息

| 项 | 值 |
| ---- | ---- |
| Base URL | `http://localhost:8080` |
| 接口前缀 | `/api` |
| 统一返回 | `Result<T>` |

### 1.2 统一返回体 `Result<T>`

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

### 1.3 状态码

| code | 含义 |
| ---- | ---- |
| 200 | 操作成功 |
| 400 | 请求参数错误 |
| 401 | 未登录或登录已过期 |
| 403 | 无权限访问 |
| 404 | 资源不存在 |
| 409 | 资源冲突（如预约时间冲突）|
| 500 | 服务器内部错误 |

### 1.4 业务字典

**资源类型 `resourceType`：**

| 值 | 含义 |
| ---- | ---- |
| 1 | 教室 |
| 2 | 实验室 |
| 3 | 图书馆 |
| 4 | 体育馆 |

**资源状态 `status`：** `1` 待使用、`2` 正在被使用、`3` 维修中

**打卡方式 `checkinType`：** 固定为 `2`（文字验证码），系统不再提供定位打卡

**违规类型 `type`：** `1` 爽约、`2` 恶意取消、`3` 其他

**维修工单状态 `status`：** `0` 待处理、`1` 处理中、`2` 已完成、`3` 已驳回

> 以上枚举逻辑已在代码层定义，未列出具体类名处均以整数传参。

### 1.5 实现进度标记

- ✅ **已实现**：接口代码已完成
- 📝 **规划中**：接口已设计，尚未编码（当前骨架阶段）

---

## 2. 资源模块

统一管理教室/实验室/图书馆/体育馆，`type` 参数区分四类。

### 2.1 分页查询资源 ✅

- **GET** `/api/resources/page`
- 请求参数：

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| current | int | 否 | 页码，默认 1 |
| size | int | 否 | 每页条数，默认 10 |
| type | int | 否 | 资源类型 1-4 |
| keyword | string | 否 | 按名称/楼宇模糊搜索 |
| status | int | 否 | 资源状态 |

- 响应示例：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [{ "id": 1, "name": "A101", "resourceType": 1, "status": 1, "reserved": false }],
    "total": 1,
    "current": 1,
    "size": 10
  }
}
```

> `reserved`：**当前时刻**是否存在有效预约（即"当前是否有人正在使用"）。前端据此把房间右上角小圆点标为红色；实验室等按"时段独占"的资源，其他时段空档不影响该标记。
>
> `reservedByMe`：当前占用者是否为本人。
>
> `unit`：资源计量单位（体育馆各场馆不同：乒乓球馆=`台`、球场=`片`，其余=`人`）。
>
> `occupiedNow`：**仅体育馆**返回，该场馆当前时段已约数量（剩余 = `capacity - occupiedNow`）。
>
> `courseBusy` / `courseNowName`：**仅教室**返回，当前是否正在上课及课程名。

### 2.2 查询资源详情 ✅

- **GET** `/api/resources/{id}`

### 2.3 新增资源 ✅

- **POST** `/api/resources`
- 请求体（JSON）：

```json
{
  "name": "A101",
  "resourceType": 1,
  "building": "第一教学楼",
  "floor": "1F",
  "roomNo": "A101",
  "capacity": 60,
  "openTime": "08:00:00",
  "closeTime": "22:00:00",
  "maxDuration": 120,
  "description": "普通教室"
}
```

### 2.4 更新资源 ✅

- **PUT** `/api/resources`

### 2.5 删除资源（逻辑删除）✅

- **DELETE** `/api/resources/{id}`

### 2.6 资源状态切换 ✅

- **PUT** `/api/resources/{id}/status/{status}`
- `status`：`2` 暂停使用 / `3` 维修中 / `1` 恢复使用

---

## 3. 用户与鉴权模块

### 3.1 用户注册 📝

- **POST** `/api/auth/register`
- 请求体：`{ "username", "password", "nickname", "phone" }`

### 3.2 登录 📝

- **POST** `/api/auth/login`
- 请求体：`{ "username", "password" }`
- 响应：`data` 返回 token 与用户基本信息

### 3.3 获取当前用户信息 📝

- **GET** `/api/users/me`
- 需登录（请求头携带 token）

### 3.4 更新个人信息 📝

- **PUT** `/api/users/me`

---

## 4. 预约模块（核心引擎）

### 4.1 创建预约（含时间冲突检测与规则校验）📝

- **POST** `/api/reservations`
- 请求体：

```json
{
  "resourceId": 1,
  "startTime": "2026-09-17 10:00:00",
  "endTime": "2026-09-17 12:00:00",
  "headcount": 1
}
```

- 服务端校验：资源开放时间、单次时长上限、信用分、**时间冲突**
- `headcount`：使用数量，**仅体育馆（`resourceType=4`）生效**，其他资源忽略（固定 1）
- 冲突规则：
  - **体育馆**：允许多人同时预约，同一时段所有有效预约的 `headcount` 之和 + 本次不得超过场馆 `capacity`，超出返回 `code: 409`
  - **其他资源**：任何时段重叠都拒绝（`code: 409`）

### 4.2 我的预约（分页）📝

- **GET** `/api/reservations/my?current=&size=&status=`
- `status` 缺省查询全部（当前、待打卡、完成、历史）

### 4.3 取消预约 📝

- **POST** `/api/reservations/{id}/cancel`
- 恶意取消/多次取消将记录违规

### 4.4 实际使用表单操作（预约详情）📝

- **GET** `/api/reservations/{id}`

### 4.5 提前释放空间 ✅

- **POST** `/api/reservations/{id}/release`
- 仅"使用中"的预约可调用；立即结束预约并释放空间（状态置为已完成）

### 4.6 暂离 / 结束暂离 ✅

- **POST** `/api/reservations/{id}/away?minutes=30|60`
- 仅"使用中"的预约可调用；暂离期间空间保留（仍锁定）
- `minutes=30` 任意时间可用；`minutes=60` 仅**午餐 11:00-12:30 / 晚餐 17:30-19:00** 可用
- 超时未结束暂离 → 系统自动释放空间并记一次违规

- **POST** `/api/reservations/{id}/away/cancel`
- 结束暂离，恢复使用

### 4.7 删除预约记录（用户端「我的预约」）✅

- **DELETE** `/api/reservations/{id}`
- 仅限**已结束**的预约（已完成 `3` / 已取消 `4` / 爽约 `5` / 违规 `6`）；删除为软删除，记录不再出现在"我的预约"
- 待打卡 `1`、使用中 `2` 不可删除，返回 `400`（请先"取消"或"提前释放"）
- 删除会在管理端写入一条操作日志（`action=9`，含用户名），保证操作可审计
- 仅可操作本人预约，管理员不受限

### 4.8 管理端：全部预约分页（含用户名与验证码）✅

- **GET** `/api/admin/reservations/page?current=&size=&username=&status=&resourceType=`
- 需管理员权限；支持按用户名、状态、资源类型筛选
- 返回记录含 `user`（用户名/昵称）、`resource`（资源信息）与 `checkinCode`（该资源当日验证码，仅"待打卡"预约有值）

### 4.9 管理端：预约操作日志分页 ✅

- **GET** `/api/admin/reservations/logs?current=&size=&username=&action=`
- 需管理员权限；`action`：`1` 创建预约、`2` 取消预约、`3` 打卡、`4` 爽约、`5` 完成、`6` 提前释放、`7` 暂离、`8` 暂离超时、`9` 删除记录
- 记录创建/取消/打卡等操作，用于管理端追溯用户操作

---

## 5. 打卡核验模块

> **打卡时间窗**：预约开始前 30 分钟 ~ 开始后 5 分钟。超过该时段未打卡，系统自动释放空间并记为爽约（扣信用分）。

### 5.1 生成/获取当日文字验证码 📝

- **GET** `/api/checkins/code/{resourceId}`
- 管理员使用，返回当日动态验证码（不存在时自动生成）

### 5.2 文字验证码打卡 📝

- **POST** `/api/checkins/verify`
- 请求体：`{ "reservationId", "code" }`

---

## 6. 位置服务模块

### 6.1 查询附近可预约资源 📝

- **GET** `/api/location/nearby?lat=&lng=&type=&radius=`
- `lat/lng` 用户当前经纬度；`type` 资源类型；`radius` 半径（米）
- 返回按距离排序的资源列表（含距离字段），用于"最近地点推荐"

---

## 7. 教室 + 课表模块

> **教室（`resourceType=1`）不参与预约**：未被课程占用时用户可直接进入使用，系统只负责展示课表占用情况。
> 用户端教室列表的红点表示"当前正在上课"，点击进入详情查看课表；调用 `POST /api/reservations` 预约教室会返回 `400`。

### 7.1 导入课表（管理端）✅

- **POST** `/api/courses/import`
- 需管理员权限；请求体为 JSON 数组，单条手动填写也走此接口
- 格式：`[{ "roomId", "courseName", "dayOfWeek", "startTime", "endTime", "teacher", "term" }]`
- `dayOfWeek`：`1~7`（周一~周日）；`startTime/endTime`：`HH:mm:ss`

### 7.2 课表列表（管理端）✅

- **GET** `/api/courses?roomId=&dayOfWeek=&building=`
- 需管理员权限；返回含教室名称/楼栋/楼层的课表记录列表

### 7.3 删除课表记录（管理端）✅

- **DELETE** `/api/courses/{id}`
- 需管理员权限

### 7.4 查询教室当日课表与空闲时段 ✅

- **GET** `/api/courses/available?roomId=&date=`
- 依据课表排除课程占用时段，返回当日课程列表与空闲时段（供用户端条形图展示）

---

## 8. 违规管理模块

### 8.1 查询违规记录（用户/管理员）📝

- **GET** `/api/violations?userId=&current=&size=`

### 8.2 手动登记违规（管理员）📝

- **POST** `/api/violations`
- 请求体：`{ "userId", "reservationId", "type", "description", "deductScore" }`

---

## 9. 维修工单模块

### 9.1 用户提交维修申请 📝

- **POST** `/api/repairs`
- 请求体：`{ "resourceId", "problem", "imageUrl" }`

### 9.2 查询维修工单列表 📝

- **GET** `/api/repairs?status=&current=&size=`

### 9.3 管理员处理维修工单 📝

- **PUT** `/api/repairs/{id}/handle`
- 请求体：`{ "status": 1, "handleUserId" }`（`1` 处理中、`2` 已完成、`3` 驳回）
- 确认维修后资源自动置为"维修中"，完成后恢复"正常使用"

---

## 10. 节假日管理模块

### 10.1 新增节假日 📝

- **POST** `/api/holidays`
- 请求体：`{ "name", "startDate", "endDate" }`

### 10.2 查询节假日列表 📝

- **GET** `/api/holidays`

### 10.3 删除节假日 📝

- **DELETE** `/api/holidays/{id}`

> 节假日期间系统自动阻止创建预约。

---

## 附：登录鉴权说明

当前骨架阶段**尚未接入登录鉴权**。规划采用：

- 登录通过 `POST /api/auth/login` 签发 token；
- 后续通过过滤器 / 拦截器校验 `Authorization` 请求头；
- 用户端与管理端通过 `sys_user.role`（`0` 用户、`1` 管理员）区分权限，需标注 `@RequiresRole` 或权限注解保证管理端接口安全。
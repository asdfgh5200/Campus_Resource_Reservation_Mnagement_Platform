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

**打卡方式 `checkinType`：** `1` 定位打卡（用户端主流程）、`2` 验证码（管理员现场补签）

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
| campus | string | 否 | 校区：南湖校区/马房山校区/余家头校区/军山校区 |
| building | string | 否 | 楼宇名称，需与校区匹配 |
| roomType | string | 否 | 细分类型，随资源类型不同：教室=多媒体教室/普通教室/学院专用/机房/制图教室/自习教室；实验室=化学/化学生物/工程实训/交通运输/管理/通用实验；体育馆=篮球/网球/田径等运动类型 |
| floor | string | 否 | 楼层，如 `1F` |

- 响应示例：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [{ "id": 1, "name": "南院-博学主楼-101", "resourceType": 1, "campus": "南湖校区", "building": "博学主楼", "floor": "1F", "roomType": "多媒体教室", "status": 1, "reserved": false }],
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
>
> `campus`：资源所属校区，取自教务系统真实校区，供前端按校区/楼栋/细分类型三级筛选。
>
> `roomType`：细分类型，三类资源各自一套语义 ——
> - **教室**（`resourceType=1`）：教务系统「教室类型」（`JASLXDM_DISPLAY`）去区域前缀后的结果，如「东院多媒体」「南院机房」→「多媒体教室」「机房」；字段为空的归入「普通教室」。
> - **实验室**（`resourceType=2`）：按楼栋归属的学科方向，化学楼→化学、化生楼→化学生物、工程训练类楼栋→工程实训、交运楼→交通运输、管理学院楼→管理，其余→通用实验。
> - **体育馆**（`resourceType=4`）：从场地名解析的运动类型（篮球 84、田径 38、网球 37、排球 20 …），无运动信息的场地归入「综合场地」。

### 2.2 校区楼栋、真实楼层与细分类型 ✅

- **GET** `/api/resources/buildings?type=`
- 按资源类型聚合各校区楼栋，供前端筛选器级联渲染；`type` 缺省返回全部类型
- 响应 `data` 为数组，每项：

```json
{
  "campus": "南湖校区",
  "building": "博学主楼",
  "total": 50,
  "floors": "1F,2F,3F,4F,5F",
  "roomTypes": "多媒体教室,普通教室,机房",
  "types": "1"
}
```

> `total` 为该楼栋资源数；`floors` 为真实存在的楼层；`roomTypes` 为该楼栋真实存在的细分类型；`types` 为该楼栋包含的资源类型（均逗号分隔）。前端据此渲染「校区 → 教学楼 → 教室类型（实验室为实验方向、体育馆为运动类型）」级联选项，不写死枚举；某校区无细分类型时前端自动隐藏该维度。

### 2.3 查询资源详情 ✅

- **GET** `/api/resources/{id}`

### 2.4 新增资源 ✅

- **POST** `/api/resources`
- 请求体（JSON）：

```json
{
  "name": "南院-博学主楼-101",
  "resourceType": 1,
  "campus": "南湖校区",
  "building": "博学主楼",
  "floor": "1F",
  "roomType": "多媒体教室",
  "roomNo": "101",
  "capacity": 170,
  "openTime": "08:00:00",
  "closeTime": "22:00:00",
  "maxDuration": 120,
  "description": "普通教室"
}
```

### 2.5 更新资源 ✅

- **PUT** `/api/resources`

### 2.6 删除资源（逻辑删除）✅

- **DELETE** `/api/resources/{id}`

### 2.7 资源状态切换 ✅

- **PUT** `/api/resources/{id}/status/{status}`
- `status`：`2` 暂停使用 / `3` 维修中 / `1` 恢复使用

---

## 3. 用户与鉴权模块

### 3.1 用户注册 📝

- **POST** `/api/auth/register`
- 请求体：`{ "nickname", "username", "password" }` —— 只收集**姓名、学号、密码**三项
- `username` 即**学号**（登录名）：4-20 位数字或字母；`nickname` 为姓名（必填，≤ 20 字）；`password` 为 6-32 位
- 学号已注册 → `code: 409`（提示直接登录）；姓名/学号/密码任一为空 → `code: 400`

### 3.2 登录 📝

- **POST** `/api/auth/login`
- 请求体：`{ "username", "password" }`，`username` 即**学号**
- 响应：`data` 返回 token 与用户基本信息
- 错误码：学号不存在 → `404`（「该学号尚未注册，请先注册」，前端据此切到注册页并预填学号）；密码错误 → `401`；账号禁用 → `403`
- 内置管理员：账号 `admin` / 密码 `admin` / 姓名 `admin`，由 `DataInitializer` 启动时保证存在（不重置已修改的密码）

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

- 服务端校验：资源开放时间、**单次时长下限 30 分钟**（不设上限）、信用分、**时间冲突**
- 时长不足 30 分钟返回 `400`（提示“单次预约时长不能少于 30 分钟”）；结束时间不得早于/等于开始时间
- `headcount`：使用数量，**仅体育馆（`resourceType=4`）生效**，其他资源忽略（固定 1）
- 冲突规则：
  - **体育馆**：允许多人同时预约，同一时段所有有效预约的 `headcount` 之和 + 本次不得超过场馆 `capacity`，超出返回 `code: 409`
  - **其他资源**：任何时段重叠都拒绝（`code: 409`）

### 4.2 我的预约（分页）📝

- **GET** `/api/reservations/my?current=&size=&status=&date=`
- `status` 缺省查询全部（当前、待打卡、完成、历史）
- `date`：可选，格式 `yyyy-MM-dd`；按**预约开始时间所在的自然日**筛选（“这天的预约”），缺省不做日期筛选
- 前端入口：“我的预约”页顶部日期选择器，精确到单日；筛出 0 条时筛选器仍保留，方便直接清空

### 4.3 取消预约 📝

- **POST** `/api/reservations/{id}/cancel`
- 仅“待打卡”状态可取消；取消成功后 `sys_user.cancel_count` +1，**服务端封顶在 5**（不会出现 6/5 这类越界值）
- 首次达到 5 次上限时记一次「恶意取消」违规；之后用户**不可再自行取消**，返回 `403`（提示通过「意见反馈」提交申诉），需管理员在用户详情页把取消次数调低后恢复
- 管理员代为取消不受上限限制（否则用户的待打卡预约无处可退），计数仍封顶在上限
- 前端入口：“我的预约”页取消按钮在达上限时置灰并附提示；`/api/violations/summary` 返回 `canCancel` 供按钮判定

### 4.4 实际使用表单操作（预约详情）📝

- **GET** `/api/reservations/{id}`

### 4.5 单条预约的「当时情况」（用户端侧拉框）✅

- **GET** `/api/reservations/{id}/trace`
- 仅可查看本人预约（管理员不受限），他人预约返回 `403`
- 前端入口：「我的预约」点击任意状态的卡片（全部/待打卡/使用中/已结束/已取消）；「个人中心 → 违规情况」点击违规记录行或「详情」，都用这一个接口还原现场
- 响应 `data`：

```json
{
  "reservation": { "id": 12, "status": 5, "startTime": "2026-10-25 13:28:22", "endTime": "2026-10-25 13:30:00", "checkinFlag": 0, "resource": { "name": "东院-东院工程训练中心-131" } },
  "logs": [{ "action": 1, "operatorRole": 0, "operatorName": "张三", "detail": "创建预约：…", "createdAt": "2026-10-25 13:28:22" }],
  "violations": [{ "id": 3, "type": 1, "description": "超过打卡时限未完成打卡，记一次爽约", "status": 1, "createdAt": "2026-10-25 13:37:00" }]
}
```

- `logs`：该预约的完整操作时间线（旧 → 新），含用户本人、管理员、系统自动三类操作人，用于展示「谁在什么时候做了什么」
- `violations`：`violation.reservation_id` 等于该预约的违规记录，**含已撤销的**，让用户看到“先记后撤销”的完整过程
- 前端据此展示：一句话状态说明、预约信息、打卡情况（方式/时间/相对开始的早晚/距离/精度）、违规记录、操作时间线，并在底部提供打卡/取消/提前释放/删除记录入口

### 4.6 提前释放空间 ✅

- **POST** `/api/reservations/{id}/release`
- 仅"使用中"的预约可调用；立即结束预约并释放空间（状态置为已完成）

### 4.7 删除预约记录（用户端「我的预约」）✅

- **DELETE** `/api/reservations/{id}`
- 仅限**已结束**的预约（已完成 `3` / 已取消 `4` / 爽约 `5` / 违规 `6`）；删除为软删除，记录不再出现在"我的预约"
- 待打卡 `1`、使用中 `2` 不可删除，返回 `400`（请先"取消"或"提前释放"）
- 删除会在管理端写入一条操作日志（`action=9`，含用户名），保证操作可审计
- 仅可操作本人预约，管理员不受限

### 4.8 管理端：全部预约分页（含用户名与验证码）✅

- **GET** `/api/admin/reservations/page?current=&size=&username=&status=&resourceType=`
- 需管理员权限；支持按用户名、状态、资源类型筛选
- 返回记录含 `user`（用户名/昵称）、`resource`（资源信息）；打卡方式与坐标在预约详情接口中查看

### 4.9 管理端：预约操作日志分页 ✅

- **GET** `/api/admin/reservations/logs?current=&size=&username=&action=`
- 需管理员权限；`action`：`1` 创建预约、`2` 取消预约、`3` 打卡、`4` 爽约、`5` 完成、`6` 提前释放、`9` 删除记录、`10` 管理员调整（`7`/`8` 为已下线的暂离，仅历史数据）
- 记录创建/取消/打卡等操作，用于管理端追溯用户操作

---

## 5. 打卡核验模块

> **打卡时间窗**：预约开始前 30 分钟 ~ 开始后 5 分钟。超过该时段未打卡，系统自动释放空间并记为爽约（扣信用分）。

### 5.1 定位打卡 ✅

- **POST** `/api/checkins/location`
- 请求体（浏览器 Geolocation 返回的 WGS-84 坐标）：

```json
{
  "reservationId": 12,
  "latitude": 30.511442,
  "longitude": 114.333556,
  "accuracy": 18.5
}
```

- 服务端仅用坐标计算用户与资源打卡点的球面距离（Haversine），**不保存用户经纬度**；操作日志只记录距离与精度结论
- 浏览器定位要求安全上下文（HTTPS 或 localhost）；首次打卡需用户授权定位

**打卡基准点**（WGS-84，同类资源共用同一坐标，由 `schema.sql` 在应用启动时同步）：

| 资源类型 | 打卡点 | 纬度 | 经度 | 打卡半径 |
| ---- | ---- | ---- | ---- | ---- |
| 1 教室 | 教学楼 | 30.511442 | 114.333556 | 120 米 |
| 2 实验室 | 学海楼 | 30.514356 | 114.345786 | 120 米 |
| 3 图书馆 | 图书馆 | 30.513169 | 114.328458 | 120 米 |
| 4 体育馆 | 体育馆 | 30.504850 | 114.330324 | 150 米 |

- 校验规则：
  - `latitude`/`longitude` 缺省或越界 → `400`
  - `accuracy` 需 > 0 且 ≤ 100 米，否则 `400`，提示“当前定位精度不足，请重试”
  - 允许距离 = 打卡半径 + min(`accuracy`, 50 米)，按定位精度放宽，最多放宽 50 米；与打卡点距离超过该值 → `400`，提示实际距离
- 成功响应：`data` 返回 `reservation`、`distance`（米）、`accuracy`（米）、`radius`（基础半径，米）、`allowedRadius`（含精度容差后的允许距离，米）

### 5.2 生成/获取当日文字验证码（管理员补签）✅

- **GET** `/api/checkins/code/{resourceId}`
- 管理员使用，返回当日动态验证码（不存在时自动生成）；用于现场无定位条件时人工补签

### 5.3 文字验证码打卡（补签）✅

- **POST** `/api/checkins/verify`
- 请求体：`{ "reservationId", "code" }`
- 打卡成功后 `checkinType = 2`，与定位打卡共用时间窗校验

---

## 6. 位置服务模块

### 6.1 查询附近可预约资源 ✅

- **GET** `/api/location/nearby?lat=&lng=&type=&radius=`
- `lat/lng` 用户当前经纬度；`type` 资源类型；`radius` 半径（米，默认 1000）
- 基于 `resource.latitude/longitude`（与打卡基准点一致）按距离升序返回，含 `distance` 字段（米）

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

- **GET** `/api/courses?roomId=&dayOfWeek=&campus=&building=`
- 需管理员权限；返回含教室名称/校区/楼栋/楼层的课表记录列表

### 7.3 删除课表记录（管理端）✅

- **DELETE** `/api/courses/{id}`
- 需管理员权限

### 7.4 查询教室当日课表与空闲时段 ✅

- **GET** `/api/courses/available?roomId=&date=`
- 依据课表排除课程占用时段，返回当日课程列表与空闲时段（供用户端条形图展示）

### 7.5 空闲教室查询（用户端）✅

- **GET** `/api/courses/free-rooms?date=&start=&end=&campus=&building=&roomType=&floor=&keyword=`
- `date`：`yyyy-MM-dd`，缺省今天；`start/end`：`HH:mm`，缺省为当前时间向上取整到半点起 2 小时
- `campus`：校区筛选，缺省不限；`building` / `roomType`：楼栋与教室类型，均可逗号分隔传多个
- `keyword`：按房间号或名称模糊搜索（如 `101`、`博学`），缺省不搜索
- 只返回**完整覆盖所选时段**、且未被课程与预约占用、非维修中的教室
- 响应 `data` 为数组，每项含 `id / name / campus / building / floor / roomType / roomNo / capacity / freeUntil`
- 前端标准调用顺序：搜索框（教室号/名称） → 校区 → 教学楼 → 教室类型 → 日期 → 时段

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
- 提交后工单为「待审核」（`status=0`），**不改变资源状态**，地点仍可正常预约；需管理员审核确认后才暂停预约
- 提交成功会向报修人推送一条「维修申请已受理」站内通知（`direction=2`，带 `resourceId`）

### 9.2 我的维修工单（用户端消息中心）📝

- **GET** `/api/repairs/mine`
- 返回当前登录用户提交的工单（最多 100 条，按提交时间倒序），每条附带 `resource`，其中 `resource.status` 为地点当前状态（`3` 维修中 / `1` 可预约），供前端跳转资源详情核对
- 驳回的工单带 `rejectReason`，前端直接展示驳回原因

### 9.3 查询维修工单列表（管理员）📝

- **GET** `/api/repairs?status=&current=&size=`
- 每条工单额外返回报修人信息（`reporterName` 姓名、`reporterAccount` 学号/账号）与 `rejectReason`，便于核实与回访

### 9.4 管理员处理维修工单 📝

- **PUT** `/api/repairs/{id}/handle`
- 请求体：`{ "status": 1, "reason": "" }`（`1` 处理中、`2` 已完成、`3` 驳回；`reason` 仅驳回时使用，不填默认「经核实暂不需要维修」）
- 审核确认（`1`）后资源才置为“维修中”并暂停预约，完成后恢复“可预约”；驳回（`3`）资源状态不变
- 每次处理都会自动向报修人推送一条进度通知（`user_message.direction=2`，带 `resourceId` 便于跳转地点）：
  - `1` →「【维修中】…已通过审核，地点已标为「维修中」并暂停预约」
  - `2` →「【维修完成】…地点已恢复「可预约」」
  - `3` →「【工单已驳回】…原因：{reason}」
- 驳回原因超过 200 字返回 `400`

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

## 11. 系统配置与闭馆模块

### 11.1 查询闭馆状态 ✅

- **GET** `/api/system/campus-status`
- 返回 `{ "closed": true }`，所有人可读，前端据此展示闭馆横幅

### 11.2 一键闭馆 / 恢复开放（管理员）✅

- **POST** `/api/system/campus-close`，请求体（可省略）：`{ "reason": "台风停课" }`
- **POST** `/api/system/campus-open`，无请求体
- 两个方向都会向**全体正常用户**群发站内通知：
  - 闭馆：「【闭馆通知】校园预约服务现已暂停（原因：台风停课），暂停期间不可新建预约或使用资源…」
  - 开放：「【恢复开放通知】校园预约服务已恢复开放，可正常新建预约与使用资源…」
- `reason` 可选，超过 100 字返回 `400`；闭馆期间普通用户创建预约返回 `400`，管理员不受限

### 11.3 定向闭馆（管理员）✅

- **POST** `/api/system/closures`，请求体：`{ "resourceType", "resourceId", "closureDate", "startTime", "endTime", "reason" }`
  - `resourceType`：`2` 实验室 / `3` 图书馆 / `4` 体育馆（教室不参与预约，不可闭馆）
  - 传 `resourceId` 时以资源真实类型为准；`closureDate` 用 `yyyy-MM-dd`，时刻用 `HH:mm:ss`
- 返回 `{ "cancelled": n }`：落在闭馆时段内的进行中/待打卡预约自动取消，并逐人推送通知
- 闭馆窗口覆盖当前时刻且范围内有资源正在使用时，返回 `409`
- **GET** `/api/system/closures`：查询全部生效中的闭馆窗口（所有人可读，前端据此禁用时段）
- **DELETE** `/api/system/closures/{id}`：撤销闭馆（仅置为不生效，已取消的预约不恢复）

### 11.4 智约机器人调用闭馆 ✅

管理员的闭馆需求由「智约机器人」转成**草案**，不直接改库：

| 用户说法 | AI 工具 | 确认后才调用的接口 |
| --- | --- | --- |
| 全站闭馆 / 恢复开放 | `admin_set_campus_closure(closed, reason)` | `POST /api/system/campus-close` / `campus-open` |
| 明天图书馆闭馆 / 今晚体育馆闭馆 | `admin_create_closure(closureDate, startTime, endTime, resourceType \| resourceId, reason)` | `POST /api/system/closures` |
| 现在闭馆了吗 / 有哪些闭馆安排 | `admin_list_closures()`（只读，立即返回） | — |
| 撤销那条闭馆 | `admin_revoke_closure(closureId)` | `DELETE /api/system/closures/{id}` |

普通用户的会话不携带这些工具（`schemasForRole` 过滤 + `dispatch` 门禁两层拦截），提问闭馆仍会回复「您没有该权限」。

---

## 12. 智约 AI 机器人

### 12.1 对话 ✅

- **POST** `/api/ai/chat`（需登录）
- 请求体：`{ "messages": [{ "role": "user", "content": "明天下午有空位的图书馆吗" }] }`
- 返回：`{ "content": "...", "pendingActions": [{ "kind", "payload" }], "assistantEcho": {...} }`
- `pendingActions` 是待确认草案，`kind` 取值：`create_reservation` / `cancel_reservation` / `campus_closure` / `create_closure` / `revoke_closure`，前端确认后才调用对应业务接口
- 未配置 `DEEPSEEK_API_KEY` 时返回 `503`；工具循环上限 `deepseek.max-tool-loops`
- 工具清单与权限模型见《AI机器人Skill说明.md》

---

## 附：登录鉴权说明

当前骨架阶段**尚未接入登录鉴权**。规划采用：

- 登录通过 `POST /api/auth/login` 签发 token；
- 后续通过过滤器 / 拦截器校验 `Authorization` 请求头；
- 用户端与管理端通过 `sys_user.role`（`0` 用户、`1` 管理员）区分权限，需标注 `@RequiresRole` 或权限注解保证管理端接口安全。
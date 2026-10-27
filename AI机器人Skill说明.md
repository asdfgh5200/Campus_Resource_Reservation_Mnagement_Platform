# 智约 AI 机器人 Skill 说明书

本文件是「智约」AI 助手的技能（Skill / Tool）规范说明，供开发、运维与提示词调优参考。
技能分两层：
- **行为指导（说明书）**：`src/main/resources/ai/skills/*.md` 分类技能文档，启动时由 `AiSkillLoader` 按登录角色加载并拼入系统提示词，DS 照着手册决定何时调哪个接口；
- **可调用接口（执行器）**：每个 `AiTool` 的 `description()` 与 `parameterSchema()`，运行时由 `AiToolRegistry` 按角色动态下发给大模型（DeepSeek，OpenAI 兼容 function calling），权限边界由代码强制执行。

```
src/main/resources/ai/skills/
├── query.md        # 查询类：query_resources / get_busy_slots / list_my_reservations
├── reservation.md  # 新建/取消类：propose_reservation / propose_cancel（草案流程）
└── admin.md        # 管理类：admin_set_campus_closure / admin_create_closure / admin_list_closures / admin_revoke_closure（仅管理员会话下发）
```

- 代码位置：`src/main/java/com/campus/reservation/ai/`
- 手册加载：`ai/AiSkillLoader.java`
- 会话编排：`service/AiChatService.java`
- 接口入口：`POST /api/ai/chat`（需登录）

---

## 1. 运行机制

1. 前端把可见对话历史提交到 `/api/ai/chat`。
2. 服务端拼装系统提示词：头部（身份+当前日期时间）+ **按角色加载的技能手册**（`AiSkillLoader.skillsForRole`）+ 尾部（预约规则/行为准则/权限约束）。
3. `AiToolRegistry.schemasForRole(role)` 依据登录者角色，只把「该角色可用」的技能 schema 交给模型。
4. 模型返回 `tool_calls` 时，`AiToolRegistry.dispatch(...)` 先做**角色二次校验**再执行；只读工具直接返回真实数据，写操作工具只产出「草案」。
5. 草案 `pendingActions` 回传前端渲染确认卡片，**用户点击确认后**前端才调用真实业务接口下单/取消。
6. 多轮工具循环上限由 `deepseek.max-tool-loops` 控制，防止死循环。

## 2. 权限模型（核心约束）

- 角色常量：`ROLE_USER = 0`（普通用户）、`ROLE_ADMIN = 1`（管理员）。
- 每个技能通过 `requiredRole()` 声明所需最低角色，默认 `ROLE_USER`。
- **两层防护**：
  1. 暴露过滤：普通用户的会话请求里根本不包含管理员级技能的 schema，模型无从调用。
  2. 执行门禁：即使模型幻觉构造出越权技能名，`dispatch` 依据服务端 `UserContext` 角色直接拒绝，返回 `{"error":"无权限执行该操作"}`。
- 数据归属：`list_my_reservations` 强制以登录用户身份查询；`propose_cancel` 校验预约归属，普通用户不能操作他人预约。
- 结论：**普通用户绝不能借 AI 行使管理员权限**（闭馆、群发通知、审批报修、查看他人数据等一律不可）。

---

## 3. 技能清单

### 3.1~3.5 普通用户技能（USER 级）

### 3.1 query_resources —— 按类型/关键词查询可用资源
- 参数：
  - `type` integer，可选，枚举 `1 教室 / 2 实验室 / 3 图书馆 / 4 体育馆`
  - `keyword` string，可选，资源名称模糊匹配
- 返回：`{"resources":[{id,name,type,building,openTime,closeTime,capacity}]}`（仅 status=1 可预约，最多 10 条）
- 调用时机：任何涉及"有哪些/某类资源"的问题，先调用它拿真实 `resourceId`，禁止编造。

### 3.2 get_busy_slots —— 查询某资源某日已占用时段
- 参数：
  - `resourceId` integer，必填
  - `date` string，必填，格式 `yyyy-MM-dd`
- 返回：`{"date":"...","busySlots":[{start:"HH:mm",end:"HH:mm"}]}`
- 调用时机：生成预约草案前，确认目标时段不与已有预约冲突。

### 3.3 list_my_reservations —— 查询我的预约
- 参数：
  - `status` integer，可选，`1 待打卡 / 2 使用中 / 3 已完成 / 4 已取消 / 5 爽约`
- 返回：`{"reservations":[{id,resourceId,resourceName,start,end,status}]}`（最多 10 条，倒序）
- 权限：强制按登录用户过滤，只能看本人。

### 3.4 propose_reservation —— 生成预约草案（不直接下单）
- 参数：
  - `resourceId` integer，必填
  - `startTime` string，必填，格式 `yyyy-MM-dd HH:mm:ss`（空格分隔，不是 ISO 的 T）
  - `endTime` string，必填，格式同上
  - `headcount` integer，可选，默认 1
  - `remark` string，可选
- 返回：`{"status":"draft_created","note":"..."}`，并向前端追加草案 `{kind:"create_reservation", payload:{...}}`
- 说明：真实下单由用户点确认后由前端调用预约接口完成；服务端仍会执行完整业务校验（开放时间、提前天数、违规、闭馆、冲突等）。

### 3.5 propose_cancel —— 生成取消预约草案（不直接取消）
- 参数：
  - `reservationId` integer，必填
- 返回：`{"status":"draft_created","note":"..."}`，并追加草案 `{kind:"cancel_reservation", payload:{reservationId}}`
- 权限：仅允许取消本人预约；对他人 `reservationId` 返回错误。

### 3.6 admin_set_campus_closure —— 一键闭馆 / 恢复开放（仅生成草案）
- 参数：
  - `closed` boolean，必填，`true` 闭馆、`false` 恢复开放
  - `reason` string，可选，会写进发给全体用户的闭馆通知
- 返回：`{"status":"draft_created"|"noop","note":"..."}`，并追加草案 `{kind:"campus_closure", payload:{closed, reason}}`
- 说明：全局开关，不带资源类型与日期；已是目标状态时返回 `noop` 不出草案；确认后 `POST /api/system/campus-close|open`，两个方向都会向全体用户群发通知。

### 3.7 admin_create_closure —— 定向闭馆（仅生成草案）
- 参数：
  - `closureDate` string，必填，`yyyy-MM-dd`
  - `startTime` / `endTime` string，必填，`HH:mm`（向后兼容 `HH:mm:ss`）
  - `resourceType` integer，可选，`2 实验室 / 3 图书馆 / 4 体育馆`（教室不可闭馆）
  - `resourceId` integer，可选，只闭某个具体资源；传了就以资源真实类型为准，`resourceType` 可省略
  - `reason` string，可选
- 返回：草案 `{kind:"create_closure", payload:{resourceType, resourceId, resourceName, closureDate, startTime, endTime, reason, scopeText}}`
- 校验：时刻已过去、结束早于开始、目标是教室、资源不存在 → 直接返回可自我纠正的错误，不产出草案。
- 说明：确认后调用 `POST /api/system/closures`，落在该时段的预约会被自动取消并逐人通知；整类闭馆还会向全体用户广播。

### 3.8 admin_list_closures —— 查询当前闭馆现状（只读，立即返回）
- 参数：无
- 返回：`{"campusClosed":bool,"count":n,"closures":[{id,scope,resourceType,resourceId,closureDate,startTime,endTime,reason}],"note":"..."}`
- 调用时机：管理员问「现在闭馆了吗 / 有哪些闭馆安排」；撤销前先用它拿到真实 `id`。

### 3.9 admin_revoke_closure —— 撤销一条定向闭馆（仅生成草案）
- 参数：`closureId` integer，必填（来自 `admin_list_closures`）
- 返回：草案 `{kind:"revoke_closure", payload:{closureId, scopeText, windowText}}`
- 校验：ID 不存在或窗口已撤销 → 返回错误不出草案；撤销不会恢复此前已自动取消的预约。

> 3.6~3.9 均覆写 `requiredRole()` 为 `ROLE_ADMIN`：普通用户会话里 schema 与提示词手册都不会出现，强行调用返回「无权限执行该操作」。


## 4. 草案（pendingActions）与前端契约

| kind | payload 字段 | 前端确认后调用的真实接口 |
| --- | --- | --- |
| `create_reservation` | `resourceId, startTime, endTime, headcount, remark, resourceName` | `POST /api/reservations` |
| `cancel_reservation` | `reservationId` | `POST /api/reservations/{id}/cancel` |
| `campus_closure` | `closed, reason` | `POST /api/system/campus-close` / `campus-open` |
| `create_closure` | `resourceType, resourceId, resourceName, closureDate, startTime, endTime, reason, scopeText` | `POST /api/system/closures` |
| `revoke_closure` | `closureId, scopeText, windowText` | `DELETE /api/system/closures/{id}` |

前端组件：`frontend/src/components/AiChatWidget.vue`（按 `kind` 渲染标题与字段，闭馆类卡片标红提示影响面）。

## 5. 预约业务规则（提示词内已告知模型）

- 同一时段每人只能一个预约；
- 实验室/体育馆最多提前 7 天，图书馆最多提前 1 天（即"明天"），教室不需预约；
- 开放时间内才可约：图书馆 22:00、体育馆 21:00、实验室 20:00；
- 需在预约开始前 30 分钟 ~ 开始后 5 分钟内，到资源所在建筑打卡范围内完成定位打卡，否则爽约；1 天内违规 3 次当日禁约；
- 总体闭馆或命中类型闭馆时段时，普通用户不可预约（管理员不受限）。

## 6. 如何新增一个技能

1. 在 `ai/tools/` 下新建类，实现 `AiTool` 接口，标注 `@Component`。
2. 实现 `name()`、`description()`、`parameterSchema()`、`requiredParams()`、`execute(ctx, args)`。
3. 若为管理员专属能力，覆写 `requiredRole()` 返回 `AiTool.ROLE_ADMIN`。
4. 写操作只产出草案，不要直接改库；只读结果建议返回 JSON 字符串。
5. **同步更新 `resources/ai/skills/` 下对应分类的 md**，按「接口速查表」格式登记调用方法（模型据此决定何时、按什么顺序调用）。
6. `AiToolRegistry` 通过注入 `List<AiTool>` 自动收集，无需改动注册表；`AiChatService` 也无需改动。
7. 在 `AiToolRegistryTest` 补充：角色过滤与越权拦截用例；新增手册文件时在 `AiSkillLoader` 的清单常量中登记。

> 注：提示词中的手册只是行为指导，模型可能偏离；权限硬边界始终由 `schemasForRole` 过滤 + `dispatch` 门禁两层代码保障，普通用户会话既看不到也执行不了管理员工具。

## 7. 配置项（application.yml）

```yaml
deepseek:
  enabled: true
  base-url: https://api.deepseek.com
  model: deepseek-chat
  api-key: ${DEEPSEEK_API_KEY:}   # 为空时 /api/ai/chat 返回 503
  max-tool-loops: 6               # 单次会话最多工具调用轮次
```

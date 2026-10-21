# 智约 AI 机器人 Skill 说明书

本文件是「智约」AI 助手的技能（Skill / Tool）规范说明，供开发、运维与提示词调优参考。
机器可读的技能定义在代码中（每个 `AiTool` 的 `description()` 与 `parameterSchema()`），
运行时由 `AiToolRegistry` 按当前登录用户角色动态下发给大模型（DeepSeek，OpenAI 兼容 function calling）。

- 代码位置：``
- 会话编排：`service/AiChatService.java`
- 接口入口：`POST /api/ai/chat`（需登录）

---

## 1. 运行机制

1. 前端把可见对话历史提交到 `/api/ai/chat`。
2. 服务端注入系统提示词（含当前日期时间、预约规则、行为准则、权限约束）。
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

## 3. 技能清单（当前均为 USER 级）

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

---

## 4. 草案（pendingActions）与前端契约

| kind | payload 字段 | 前端确认后调用的真实接口 |
| --- | --- | --- |
| `create_reservation` | `resourceId, startTime, endTime, headcount, remark, resourceName` | `POST /api/reservations` |
| `cancel_reservation` | `reservationId` | `POST /api/reservations/{id}/cancel` |

前端组件：`../../../../../../../frontend/src/components/AiChatWidget.vue`。

## 5. 预约业务规则（提示词内已告知模型）

- 同一时段每人只能一个预约；
- 实验室/体育馆最多提前 7 天，图书馆最多提前 1 天（即"明天"），教室不需预约；
- 开放时间内才可约：图书馆 22:00、体育馆 21:00、实验室 20:00；
- 预约后 30 分钟内验证码打卡，否则爽约；1 天内违规 3 次当日禁约；
- 总体闭馆或命中类型闭馆时段时，普通用户不可预约（管理员不受限）。

## 6. 如何新增一个技能

1. 在 `ai/tools/` 下新建类，实现 `AiTool` 接口，标注 `@Component`。
2. 实现 `name()`、`description()`、`parameterSchema()`、`requiredParams()`、`execute(ctx, args)`。
3. 若为管理员专属能力，覆写 `requiredRole()` 返回 `AiTool.ROLE_ADMIN`。
4. 写操作只产出草案，不要直接改库；只读结果建议返回 JSON 字符串。
5. `AiToolRegistry` 通过注入 `List<AiTool>` 自动收集，无需改动注册表；`AiChatService` 也无需改动。
6. 在 `AiToolRegistryTest` 补充：角色过滤与越权拦截用例。

## 7. 配置项（application.yml）

```yaml
deepseek:
  enabled: true
  base-url: https://api.deepseek.com
  model: deepseek-chat
  api-key: ${DEEPSEEK_API_KEY:}   # 为空时 /api/ai/chat 返回 503
  max-tool-loops: 6               # 单次会话最多工具调用轮次
```
